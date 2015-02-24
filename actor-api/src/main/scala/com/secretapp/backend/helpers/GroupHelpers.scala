package com.secretapp.backend.helpers

import akka.actor._
import akka.util.Timeout
import com.secretapp.backend.api.UpdatesBroker
import com.secretapp.backend.data.message.struct
import com.secretapp.backend.data.message.rpc.{ RpcResponse, Error}
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.models
import com.secretapp.backend.persist
import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.Scalaz._

trait GroupHelpers extends UserHelpers with UpdatesHelpers {
  val context: ActorContext

  implicit val timeout: Timeout

  import context.dispatcher

  def getGroupStruct(groupId: Int, currentUserId: Int)(implicit s: ActorSystem): Future[Option[struct.Group]] = {
    for {
      optGroupModelWithAvatar <- persist.Group.findWithAvatar(groupId)
      groupUserMembers <- persist.GroupUser.findGroupUserIdsWithMeta(groupId)
    } yield {
      optGroupModelWithAvatar map {
        case (group, avatarData) =>
          struct.Group.fromModel(
            group = group,
            groupMembers = groupUserMembers.toVector map {
              case (userId, persist.GroupUserMeta(inviterUserId, date)) =>
                struct.Member(userId, inviterUserId, date.getMillis)
            },
            isMember = groupUserMembers.find(_._1 == currentUserId).isDefined,
            optAvatar = avatarData.avatar
          )
      }
    }
  }

  def getGroupUserIds(groupId: Int): Future[Seq[Int]] = {
    persist.GroupUser.findGroupUserIds(groupId)
  }

  def getGroupUserIdsWithAuthIds(groupId: Int): Future[Seq[(Int, Seq[Long])]] = {
    persist.GroupUser.findGroupUserIds(groupId) flatMap { userIds =>
      Future.sequence(
        userIds map { userId =>
          getAuthIds(userId) map ((userId, _))
        }
      )
    }
  }

  def getGroupMembersWithAuthIds(groupId: Int): Future[Seq[(struct.Member, Seq[Long])]] = {
    persist.GroupUser.findGroupUserIdsWithMeta(groupId) flatMap { userIdsWithMeta =>
      Future.sequence(
        userIdsWithMeta map {
          case (userId, persist.GroupUserMeta(inviterUserId, date)) =>
            getAuthIds(userId) map ((struct.Member(userId, inviterUserId, date.getMillis), _))
        }
      )
    }
  }

  def getGroupUserAuthIds(groupId: Int): Future[Seq[Long]] = {
    getGroupUserIds(groupId) flatMap {
      case groupUserIds =>
        Future.sequence(
          groupUserIds map { groupUserId =>
            getAuthIds(groupUserId)
          }
        ) map (_.flatten)
    }
  }

  def foreachGroupUserAuthId(groupId: Int)(f: Long => Any) =
    getGroupUserAuthIds(groupId) onSuccess {
      case authIds =>
        authIds foreach f
    }

  def foreachGroupUserIdsWithAuthIds(groupId: Int)(f: ((Int , Seq[Long])) => Any) =
    getGroupUserIdsWithAuthIds(groupId) onSuccess {
      case userIdsAuthIds =>
        userIdsAuthIds foreach f
    }

  def leaveGroup(groupId: Int, randomId: Long, currentUser: models.User): Future[Error \/ UpdatesBroker.StrictState] = {
    val userIdsAuthIdsF = getGroupUserIdsWithAuthIds(groupId)
    val date = System.currentTimeMillis()

    userIdsAuthIdsF flatMap { userIdsAuthIds =>
      if (userIdsAuthIds.toMap.contains(currentUser.uid)) {
        val rmUserF = persist.GroupUser.removeGroupUser(groupId, currentUser.uid)

        val userLeaveUpdate = GroupUserLeave(
          groupId = groupId,
          randomId = randomId,
          userId = currentUser.uid,
          date = date
        )

        userIdsAuthIds foreach {
          case (userId, authIds) =>
            val targetAuthIds = if (userId != currentUser.uid) {
              authIds
            } else {
              authIds.filterNot(_ == currentUser.authId)
            }

            targetAuthIds foreach (writeNewUpdate(_, userLeaveUpdate))
        }

        writeNewUpdateAndGetState(
          currentUser.authId,
          userLeaveUpdate
        ) map (_.right)
      } else {
        Future.successful(Error(400, "ALREADY_LEFT", "You already left this group.", false).left)
      }
    }
  }
}
