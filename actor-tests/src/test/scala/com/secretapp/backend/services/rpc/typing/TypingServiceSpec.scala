package com.secretapp.backend.services.rpc.typing

import com.secretapp.backend.data.message.UpdateBox
import com.secretapp.backend.data.message.rpc.ResponseVoid
import com.secretapp.backend.data.message.rpc.messaging._
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.data.message.rpc.typing._
import com.secretapp.backend.data.message.struct
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.data.message.update.WeakUpdate
import com.secretapp.backend.services.rpc.RpcSpec
import com.secretapp.backend.util.ACL
import scala.collection.immutable
import scodec.bits._

class TypingServiceSpec extends RpcSpec {
  "presence service" should {
    "send typings on subscribtion and receive typing weak updates" in new sqlDb {
      val (scope1, scope2) = TestScope.pair(1, 2)
      catchNewSession(scope1)
      catchNewSession(scope2)

      {
        implicit val scope = scope1

        RequestTyping(struct.OutPeer.privat(scope2.user.uid, ACL.userAccessHash(scope.user.authId, scope2.user)), 1) :~> <~:[ResponseVoid]
      }

      {
        implicit val scope = scope2

        val (_, updates) = RequestGetState() :~> <~:[ResponseSeq]

        val update = updates match {
          case Nil => receiveNMessageBoxes(1)(scope.probe, scope.apiActor).head.body
          case xs => xs.head
        }

        update should beAnInstanceOf[UpdateBox]
        update.asInstanceOf[UpdateBox].body should beAnInstanceOf[WeakUpdate]
      }
    }

    "send typings weak updates" in new sqlDb {
      val (scope1, scope2) = TestScope.pair(1, 2)
      catchNewSession(scope1)
      catchNewSession(scope2)

      {
        implicit val scope = scope1

        RequestGetState() :~> <~:[ResponseSeq]

      }

      {
        implicit val scope = scope2

        RequestTyping(struct.OutPeer.privat(scope1.user.uid, ACL.userAccessHash(scope.user.authId, scope1.user)), 1) :~> <~:[ResponseVoid]
      }

      {
        implicit val scope = scope1

        val update = receiveNMessageBoxes(1)(scope.probe, scope.apiActor).head.body
        update should beAnInstanceOf[UpdateBox]
        update.asInstanceOf[UpdateBox].body should beAnInstanceOf[WeakUpdate]
      }
    }

    "send group typings weak updates" in new sqlDb {
      val (scope1, scope2) = TestScope.pair(3, 4)
      catchNewSession(scope1)
      catchNewSession(scope2)

      val respGroup = {
        implicit val scope = scope1

        val rqCreateGroup = RequestCreateGroup(
          randomId = 1L,
          title = "Groupgroup 3000",
          users = immutable.Seq(
            struct.UserOutPeer(scope2.user.uid, ACL.userAccessHash(scope.user.authId, scope2.user))
          )
        )
        val (resp, _) = rqCreateGroup :~> <~:[ResponseCreateGroup]

        RequestGetState() :~> <~:[ResponseSeq]

        resp
      }

      {
        implicit val scope = scope2

        RequestTyping(struct.OutPeer.group(respGroup.groupPeer.id, respGroup.groupPeer.accessHash), 1) :~> <~:[ResponseVoid]
      }

      {
        implicit val scope = scope1

        val updateBox = receiveNMessageBoxes(1)(scope.probe, scope.apiActor).head.body
        val update = updateBox.assertInstanceOf[UpdateBox].body.assertInstanceOf[WeakUpdate].body.assertInstanceOf[Typing]
        update.userId should_== scope2.user.uid
        update.peer.id should_== respGroup.groupPeer.id
      }
    }
  }
}
