package com.secretapp.backend.session

import com.secretapp.backend.api.frontend._
import com.secretapp.backend.data.transport._
import com.secretapp.backend.protocol.codecs.message._
import scodec.bits.BitVector

trait TransportSerializers {
  val authId: Long
  val sessionId: Long

  var transport: Option[TransportConnection] = None

  def serializeMessageBox(message: MessageBox): BitVector = {
    transport match {
      case Some(MTConnection) => MessageBoxCodec.encodeValid(message)
      case None =>
        MessageBoxCodec.encodeValid(message)
        //throw new IllegalArgumentException("transport == None")
    }
  }

  def serializePackage(mb: BitVector): ResponseToClient = transport match {
    case Some(MTConnection) => ResponseToClient(MTPackage(authId, sessionId, mb).encode)
    case None =>
      ResponseToClient(MTPackage(authId, sessionId, mb).encode)
      //throw new IllegalArgumentException("transport == None")
  }

  def serializePackage(message: MessageBox): ResponseToClient = serializePackage(serializeMessageBox(message))
}
