package com.secretapp.backend.data.message.rpc.file

import com.secretapp.backend.data.message.rpc._
import scodec.bits.BitVector

@SerialVersionUID(1L)
case class RequestUploadPart(config: UploadConfig, offset: Int, data: BitVector) extends RpcRequestMessage {
  val header = RequestUploadPart.header
}

object RequestUploadPart extends RpcRequestMessageObject {
  val header = 0x14
}
