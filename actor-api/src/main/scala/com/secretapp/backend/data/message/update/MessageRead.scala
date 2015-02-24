package com.secretapp.backend.data.message.update

import com.secretapp.backend.data.message.struct
import com.secretapp.backend.models

@SerialVersionUID(1L)
case class MessageRead(peer: struct.Peer, date: Long, readDate: Long) extends SeqUpdateMessage {
  val header = MessageRead.header

  def userIds: Set[Int] = Set(peer.id)

  def groupIds: Set[Int] = peer.typ match {
    case models.PeerType.Group =>
      Set(peer.id)
    case _ =>
      Set.empty
  }
}

object MessageRead extends SeqUpdateMessageObject {
  val header = 0x13
}
