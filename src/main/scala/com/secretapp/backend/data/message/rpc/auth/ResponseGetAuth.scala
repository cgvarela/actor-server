package com.secretapp.backend.data.message.rpc.auth

import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.data.message.struct
import scala.collection.immutable

@SerialVersionUID(1L)
case class ResponseGetAuth(userAuths: immutable.Seq[struct.AuthItem]) extends RpcResponseMessage {
  val header = ResponseGetAuth.responseType
}

object ResponseGetAuth extends RpcResponseMessageObject {
  val responseType = 0x51
}