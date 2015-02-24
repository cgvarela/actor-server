package com.secretapp.backend.session

import akka.actor._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{ Subscribe, Unsubscribe }
import com.secretapp.backend.api.ApiBrokerProtocol
import com.secretapp.backend.api.UpdatesBroker
import com.secretapp.backend.data.transport.MessageBox
import com.secretapp.backend.data.transport.MTPackage
import com.secretapp.backend.persist
import com.secretapp.backend.protocol.transport._
import com.secretapp.backend.services.UserManagerService
import com.secretapp.backend.services.common.PackageCommon
import com.secretapp.backend.services.common.PackageCommon._
import com.secretapp.backend.data.message._
import com.secretapp.backend.services.rpc.presence.{ GroupPresenceBroker, GroupPresenceProtocol, PresenceBroker, PresenceProtocol }
import com.secretapp.backend.services.rpc.typing.{ TypingBroker, TypingProtocol }
import scala.collection.immutable
import scala.concurrent.duration._
import scalaz._
import Scalaz._

trait SessionService extends UserManagerService {
  self: SessionActor =>
  import AckTrackerProtocol._
  import ApiBrokerProtocol._

  import context.dispatcher

  var subscribedToUpdates = false
  var subscribingToUpdates = false

  var subscribedToPresencesUids = immutable.Set.empty[Int]
  var subscribedToPresencesGroupIds = immutable.Set.empty[Int]

  protected def handleMessage(connector: ActorRef, mb: MessageBox): Unit = {
    acknowledgeReceivedPackage(connector, mb)

    mb.body match { // TODO: move into pluggable traits
      case Ping(randomId) =>
        val reply = serializePackage(MessageBox(getMessageId(TransportMsgId), Pong(randomId)))
        connector.tell(reply, context.self)
      case MessageAck(mids) =>
        ackTracker.tell(RegisterMessageAcks(mids.toList), context.self)
      case RpcRequestBox(body) =>
        apiBroker.tell(ApiBrokerRequest(connector, mb.messageId, body), context.self)
      case x =>
        withMDC(log.error(s"unhandled session message $x"))
    }
  }

  protected def subscribeToPresences(userIds: immutable.Seq[Int]) = {
    userIds foreach { userId =>
      if (!subscribedToPresencesUids.contains(userId)) {
        val topic = PresenceBroker.topicFor(userId)
        withMDC(log.debug(s"Subscribing $userId $topic"))
        subscribedToPresencesUids = subscribedToPresencesUids + userId
        mediator ! Subscribe(
          topic,
          weakUpdatesPusher
        )
      } else {
        withMDC(log.warning(s"Already subscribed to $userId"))
      }

      singletons.presenceBrokerRegion ! PresenceProtocol.Envelope(
        userId,
        PresenceProtocol.TellPresence(weakUpdatesPusher)
      )
    }
  }

  protected def recoverSubscribeToPresences(userIds: immutable.Seq[Int]) = {
    userIds foreach { userId =>
      withMDC(log.info(s"Subscribing $userId"))
      subscribedToPresencesUids = subscribedToPresencesUids + userId
      mediator ! Subscribe(
        PresenceBroker.topicFor(userId),
        weakUpdatesPusher
      )

      singletons.presenceBrokerRegion ! PresenceProtocol.Envelope(
        userId,
        PresenceProtocol.TellPresence(weakUpdatesPusher)
      )
    }
  }

  protected def unsubscribeToPresences(uids: immutable.Seq[Int]) = {
    uids foreach { userId =>
      subscribedToPresencesUids = subscribedToPresencesUids - userId
      mediator ! Unsubscribe(
        PresenceBroker.topicFor(userId),
        weakUpdatesPusher
      )
    }
  }

  protected def subscribeToGroupPresences(groupIds: immutable.Seq[Int]) = {
    groupIds foreach { groupId =>
      if (!subscribedToPresencesGroupIds.contains(groupId)) {
        withMDC(log.info(s"Subscribing to group presences groupId=$groupId"))
        subscribedToPresencesGroupIds = subscribedToPresencesGroupIds + groupId
        mediator ! Subscribe(
          GroupPresenceBroker.topicFor(groupId),
          weakUpdatesPusher
        )
      } else {
        withMDC(log.error(s"Already subscribed to $groupId"))
      }

      singletons.groupPresenceBrokerRegion ! GroupPresenceProtocol.Envelope(
        groupId,
        GroupPresenceProtocol.TellPresences(weakUpdatesPusher)
      )
    }
  }

  protected def recoverSubscribeToGroupPresences(groupIds: immutable.Seq[Int]) = {
    groupIds foreach { groupId =>
      withMDC(log.info(s"Subscribing to group presences groupId=$groupId"))
      subscribedToPresencesGroupIds = subscribedToPresencesGroupIds + groupId
      mediator ! Subscribe(
        GroupPresenceBroker.topicFor(groupId),
        weakUpdatesPusher
      )

      singletons.groupPresenceBrokerRegion ! GroupPresenceProtocol.Envelope(
        groupId,
        GroupPresenceProtocol.TellPresences(weakUpdatesPusher)
      )
    }
  }

  protected def unsubscribeFromGroupPresences(groupIds: immutable.Seq[Int]) = {
    groupIds foreach { groupId =>
      subscribedToPresencesGroupIds = subscribedToPresencesGroupIds - groupId
      mediator ! Unsubscribe(
        GroupPresenceBroker.topicFor(groupId),
        weakUpdatesPusher
      )
    }
  }

  protected def subscribeToUpdates() = {
    subscribingToUpdates = true
    withMDC(log.debug(s"Subscribing to updates"))
    mediator ! Subscribe(UpdatesBroker.topicFor(authId), seqUpdatesPusher)

    subscribeToTypings()
  }

  protected def subscribeToTypings(): Unit = {
    if (currentUser.isDefined) {
      mediator ! Subscribe(TypingBroker.topicFor(currentUser.get.uid), weakUpdatesPusher)
      mediator ! Subscribe(TypingBroker.topicFor(currentUser.get.uid, currentUser.get.authId), weakUpdatesPusher)

      singletons.typingBrokerRegion ! TypingProtocol.Envelope(
        currentUser.get.uid,
        TypingProtocol.TellTypings(weakUpdatesPusher)
      )
    } else { // wait for AuthorizeUser message
      withMDC(log.debug("Waiting for AuthorizeUser and try to subscribe to typings again"))
      context.system.scheduler.scheduleOnce(500.milliseconds) {
        subscribeToTypings()
      }
    }
  }

  protected def handleSubscribeAck(subscribe: Subscribe) = {
    withMDC(log.debug("Handling subscribe ack {}", subscribe))
    if (subscribe.topic == UpdatesBroker.topicFor(authId) && subscribe.ref == seqUpdatesPusher) {
      subscribingToUpdates = false
      subscribedToUpdates = true
    } else if (subscribe.topic.startsWith("presences-") && subscribe.ref == seqUpdatesPusher) {
      // FIXME: don't use startsWith here

      // TODO: implement ack handling
    }
  }
}
