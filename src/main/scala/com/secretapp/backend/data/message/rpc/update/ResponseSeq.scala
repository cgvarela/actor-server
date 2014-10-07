package com.secretapp.backend.data.message.rpc.update

import com.secretapp.backend.data.message.rpc._
import java.util.UUID

@SerialVersionUID(1l)
case class ResponseSeq(seq: Int, state: Option[UUID]) extends RpcResponseMessage {
  val header = ResponseSeq.responseType
}

object ResponseSeq extends RpcResponseMessageObject {
  val responseType = 0x48
}
