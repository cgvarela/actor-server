package com.secretapp.backend.data.message

import com.secretapp.backend.data.message.rpc.RpcResponseMessage

case class RpcResponseBox(messageId : Long, body : RpcResponseMessage) extends TransportMessage
object RpcResponseBox {
  val header = 0x4
}
