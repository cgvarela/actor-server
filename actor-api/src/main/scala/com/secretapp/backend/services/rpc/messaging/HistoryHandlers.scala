package com.secretapp.backend.services.rpc.messaging

import akka.actor._
import akka.pattern.ask
import com.datastax.driver.core.{ Session => CSession }
import com.secretapp.backend.api.{ SocialProtocol, UpdatesBroker }
import com.secretapp.backend.data.message.struct
import com.secretapp.backend.data.message.rpc.messaging._
import com.secretapp.backend.data.message.rpc.history._
import com.secretapp.backend.data.message.rpc.{ Error, Ok, RpcResponse, ResponseVoid }
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.data.message.{ update => updateProto }
import com.secretapp.backend.models
import com.secretapp.backend.helpers.{HistoryHelpers, GroupHelpers, UserHelpers}
import com.secretapp.backend.persist
import com.secretapp.backend.services.common.RandomService
import com.secretapp.backend.util.{ACL, AvatarUtils}
import java.util.UUID
import org.joda.time.DateTime
import scala.collection.immutable
import scala.concurrent.Future
import scala.language.postfixOps
import scalaz.Scalaz._
import scalaz._
import scodec.bits._

trait HistoryHandlers extends RandomService with UserHelpers {
  self: Handler =>

  import context.{ dispatcher, system }
  import UpdatesBroker._

  implicit val session: CSession

  val handleHistory: RequestMatcher = {
    case RequestLoadHistory(peer, randomId, message) =>
      handleRequestLoadHistory(peer, randomId, message)
    case RequestLoadDialogs(startDate, limit) =>
      handleRequestLoadDialogs(startDate, limit)
    case RequestDeleteMessage(outPeer, randomIds) =>
      handleRequestDeleteMessage(outPeer, randomIds)
    case RequestClearChat(outPeer) =>
      handleRequestClearChat(outPeer)
    case RequestDeleteChat(outPeer) =>
      handleRequestDeleteChat(outPeer)
  }

  protected def handleRequestDeleteMessage(
    outPeer: struct.OutPeer,
    randomIds: immutable.Seq[Long]
  ): Future[RpcResponse] = {
    withOutPeer(outPeer, currentUser) {
      markMessageDeleted(currentUser.uid, outPeer.asPeer.asModel, randomIds)
      val authIdsF = outPeer.typ match {
        case models.PeerType.Group =>
          getGroupUserAuthIds(outPeer.id)
        case models.PeerType.Private =>
          val ownAuthIdsF = getAuthIds(currentUser.uid)
          val outAuthIdsF = getAuthIds(outPeer.id)

          for {
            ownAuthIds <- ownAuthIdsF
            outAuthIds <- outAuthIdsF
          } yield {
            ownAuthIds ++ outAuthIds
          }
      }

      val update = updateProto.MessageDelete(outPeer.asPeer, randomIds)

      for (authIds <- authIdsF) {
        authIds foreach (writeNewUpdate(_, update))
      }

      Future.successful(Ok(ResponseVoid()))
    }
  }

  protected def handleRequestClearChat(
    outPeer: struct.OutPeer
  ): Future[RpcResponse] = {
    withOutPeer(outPeer, currentUser) {
      persist.HistoryMessage.destroyAll(currentUser.uid, outPeer.asPeer.asModel)

      val update = updateProto.ChatClear(outPeer.asPeer)

      for (authIds <- getAuthIds(currentUser.uid)) yield {
        authIds foreach { authId =>
          if (authId != currentUser.authId)
            writeNewUpdate(authId, update)
        }
      }

      withNewUpdateState(currentUser.authId, update) { s =>
        Ok(ResponseSeq(s._1, Some(s._2)))
      }
    }
  }

  protected def handleRequestDeleteChat(
    outPeer: struct.OutPeer
  ): Future[RpcResponse] = withOutPeer(outPeer, currentUser) {
    persist.HistoryMessage.destroyAll(currentUser.uid, outPeer.asPeer.asModel)
    persist.Dialog.destroy(currentUser.uid, outPeer.asPeer.asModel)

    val update = updateProto.ChatDelete(outPeer.asPeer)

    for (authIds <- getAuthIds(currentUser.uid)) yield {
      authIds foreach { authId =>
        if (authId != currentUser.authId)
          writeNewUpdate(authId, update)
      }
    }

    withNewUpdateState(currentUser.authId, update) { s =>
      Ok(ResponseSeq(s._1, Some(s._2)))
    }
  }

  // TODO: refactor
  protected def handleRequestLoadDialogs(
    startDate: Long,
    limit: Int
  ): Future[RpcResponse] = {
    persist.Dialog.findAllWithUnreadCount(currentUser.uid, new DateTime(startDate), limit) flatMap { dmWithUnread =>
      val dialogs: Vector[Dialog] = dmWithUnread.foldLeft(Vector.empty[Dialog]) {
        case (res, Tuple2(models.Dialog(_, peer, sortDate, senderUserId, randomId, date, mcHeader, mcData, state), unreadCount)) =>
          val stateOpt = if (currentUser.uid == senderUserId) {
            Some(state) // for outgoing
          } else {
            None // for incoming
          }

          res :+ Dialog(
            peer = struct.Peer.fromModel(peer),
            unreadCount = unreadCount.toInt,
            sortDate = sortDate.getMillis,
            senderUserId = senderUserId,
            randomId = randomId,
            date = date.getMillis,
            message = MessageContent.build(mcHeader, mcData),
            state = stateOpt
          )
      }

      val (usersFutures, groupsFutures) = dialogs.foldLeft((Vector.empty[Future[Option[struct.User]]], Vector.empty[Future[Option[struct.Group]]])) {
        case (res, dialog) =>
          dialog.peer.typ match {
            case models.PeerType.Private =>
              res.copy(
                _1 = res._1 :+ getUserStruct(dialog.peer.id, currentUser.authId)
              )
            case models.PeerType.Group =>
              res.copy(
                _2 = res._2 :+ getGroupStruct(dialog.peer.id, currentUser.uid)
              )
          }
      }

      for {
        users <- Future.sequence(usersFutures)
        groups <- Future.sequence(groupsFutures)
      } yield {
        Ok(ResponseLoadDialogs(
          groups = groups.flatten,
          users = users.flatten,
          dialogs = dialogs
        ))
      }
    }
  }

  protected def handleRequestLoadHistory(
    outPeer: struct.OutPeer,
    startDate: Long,
    limit: Int
  ): Future[RpcResponse] = {
    withOutPeer(outPeer, currentUser) {
      persist.HistoryMessage.findAll(currentUser.uid, outPeer.asPeer.asModel, new DateTime(startDate), limit) flatMap { messages =>
        val userIds = messages.foldLeft(Set.empty[Int]) { (res, message) =>
          if (message.senderUserId != currentUser.uid)
            res + message.senderUserId
          else
            res
        }

        val usersF = Future.sequence(userIds map (getUserStruct(_, currentUser.authId))) map (_.flatten)

        for (users <- usersF) yield {
          Ok(ResponseLoadHistory(messages.toVector map HistoryMessage.fromModel, users.toVector))
        }
      }
    }
  }
}
