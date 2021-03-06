package com.secretapp.backend.data.message.update.contact

import com.secretapp.backend.data.message.update._
import im.actor.messenger.{ api => protobuf }

@SerialVersionUID(1L)
case class LocalNameChanged(userId: Int, localName: Option[String]) extends SeqUpdateMessage {
  val header = LocalNameChanged.header

  def userIds: Set[Int] = Set(userId)

  def groupIds: Set[Int] = Set.empty
}

object LocalNameChanged extends SeqUpdateMessageObject {
  val header = 0x33
}
