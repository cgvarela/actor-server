package com.secretapp.backend.protocol.codecs.message.update.contact

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data.message.update.contact._
import com.secretapp.backend.protocol.codecs.utils.protobuf._
import scodec.bits._
import scodec.Codec
import scalaz._
import Scalaz._
import scala.util.Success
import im.actor.messenger.{ api => protobuf }

object LocalNameChangedCodec extends Codec[LocalNameChanged] with utils.ProtobufCodec {
  def encode(u: LocalNameChanged) = {
    val boxed = protobuf.UpdateUserLocalNameChanged(u.userId, u.localName)
    encodeToBitVector(boxed)
  }

  def decode(buf: BitVector) = {
    decodeProtobuf(protobuf.UpdateUserLocalNameChanged.parseFrom(buf.toByteArray)) {
      case Success(r) => LocalNameChanged(r.uid, r.localName)
    }
  }
}
