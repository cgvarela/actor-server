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
import im.actor.messenger.{ api => protobuf }

object GroupOnlineCodec extends Codec[GroupOnline] with utils.ProtobufCodec {
  def encode(u: GroupOnline) = {
    val boxed = protobuf.UpdateGroupOnline(u.groupId, u.count)
    encodeToBitVector(boxed)
  }

  def decode(buf: BitVector) = {
    decodeProtobuf(protobuf.UpdateGroupOnline.parseFrom(buf.toByteArray)) {
      case Success(protobuf.UpdateGroupOnline(groupId, count)) => GroupOnline(groupId, count)
    }
  }
}
