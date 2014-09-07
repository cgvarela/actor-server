package com.secretapp.backend.data.message

case class MessageAck(messageIds: Vector[Long]) extends TransportMessage
object MessageAck extends TransportMessageMessageObject {
  val header = 0x6
}
