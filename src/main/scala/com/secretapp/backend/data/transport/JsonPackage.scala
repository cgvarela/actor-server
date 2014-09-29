package com.secretapp.backend.data.transport

import akka.util.ByteString
import com.secretapp.backend.data.message.TransportMessage
import com.secretapp.backend.protocol.codecs.message.{JsonMessageBoxCodec, MessageBoxCodec}
import scodec.bits._

case class JsonPackage(authId: Long, sessionId: Long, messageBoxBytes: BitVector) extends TransportPackage {
  @deprecated("replyWith should be moved to MessageBox", "")
  def replyWith(messageId: Long, tm: TransportMessage): JsonPackage = {
    val mb = MessageBox(messageId, tm)
    JsonPackage(authId, sessionId, JsonMessageBoxCodec.encodeValid(mb))
  }

  // TODO
  @deprecated("move into JsonPackageCodec", "")
  def toJson: ByteString = {
    ByteString(s"[${this.authId},${this.sessionId},") ++ ByteString(this.messageBoxBytes.toByteBuffer) ++ ByteString("]")
  }
}
