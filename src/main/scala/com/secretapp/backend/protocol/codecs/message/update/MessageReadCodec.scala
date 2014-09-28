package com.secretapp.backend.protocol.codecs.message.update

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.protocol.codecs.utils.protobuf._
import scodec.bits._
import scodec.Codec
import scodec.codecs._
import scalaz._
import Scalaz._
import scala.util.Success
import com.reactive.messenger.{ api => protobuf }

object MessageReadCodec extends Codec[MessageRead] with utils.ProtobufCodec {
  def encode(u: MessageRead) = {
    val boxed = protobuf.UpdateMessageRead(u.uid, u.randomId)
    encodeToBitVector(boxed)
  }

  def decode(buf: BitVector) = {
    decodeProtobuf(protobuf.UpdateMessageRead.parseFrom(buf.toByteArray)) {
      case Success(protobuf.UpdateMessageRead(uid, randomId)) => MessageRead(uid, randomId)
    }
  }
}
