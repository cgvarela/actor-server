package com.secretapp.backend.data.message.rpc.messaging

import scala.language.implicitConversions
import com.secretapp.backend.data.types
import com.secretapp.backend.data.message.ProtobufMessage
import com.secretapp.backend.protocol.codecs.utils.protobuf._
import com.reactive.messenger.{ api => protobuf }
import scodec.bits.BitVector
import scalaz._
import Scalaz._

case class EncryptedMessage(uid: Int,
                            publicKeyHash: Long,
                            aesEncryptedKey: Option[BitVector],
                            message: Option[BitVector]) extends ProtobufMessage
{
  def toProto = protobuf.EncryptedMessage(uid, publicKeyHash, aesEncryptedKey, message)
}

object EncryptedMessage {
  def fromProto(u: protobuf.EncryptedMessage): EncryptedMessage = u match {
    case protobuf.EncryptedMessage(uid, publicKeyHash, aesEncryptedKey, message) =>
      EncryptedMessage(uid, publicKeyHash, aesEncryptedKey, message)
  }
}
