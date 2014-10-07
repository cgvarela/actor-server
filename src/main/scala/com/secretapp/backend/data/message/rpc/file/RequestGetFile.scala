package com.secretapp.backend.data.message.rpc.file

import com.secretapp.backend.data.message.rpc._

@SerialVersionUID(1l)
case class RequestGetFile(fileLocation: FileLocation, offset: Int, limit: Int) extends RpcRequestMessage {
  val header = RequestGetFile.requestType
}

object RequestGetFile extends RpcRequestMessageObject {
  val requestType = 0x10
}
