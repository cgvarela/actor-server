package com.secretapp.backend.services.rpc.push

import akka.actor.{ActorLogging, Actor}
import akka.pattern.pipe
import com.secretapp.backend.api.rpc.RpcProtocol
import com.secretapp.backend.data.message.rpc.push._
import com.secretapp.backend.models.User

class Handler(val currentAuthId: Long) extends Actor with ActorLogging with HandlerService {
  import context._

  override def receive = {
    case RpcProtocol.Request(RequestRegisterGooglePush(projectId, token)) =>
      handleRequestRegisterGooglePush(projectId, token) pipeTo sender

    case RpcProtocol.Request(RequestRegisterApplePush(apnsKey, token)) =>
      handleRequestRegisterApplePush(apnsKey, token) pipeTo sender

    case RpcProtocol.Request(RequestUnregisterPush()) =>
      handleRequestUnregisterPush pipeTo sender
  }
}
