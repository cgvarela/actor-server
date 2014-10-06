package com.secretapp.backend.api.frontend.ws

import akka.actor._
import akka.util.ByteString
import akka.util.Timeout
import com.datastax.driver.core.{ Session => CSession }
import com.secretapp.backend.api.frontend._
import scodec.bits.BitVector
import spray.can.websocket
import spray.can.websocket.frame._
import spray.can.websocket.Send
import spray.http.HttpRequest
import spray.can.websocket.FrameCommandFailed
import spray.routing.HttpServiceActor
import com.secretapp.backend.protocol.transport.{JsonPackageCodec, Frontend}
import scalaz._
import Scalaz._

object WSFrontend {
  def props(connection: ActorRef, sessionRegion: ActorRef, session: CSession) = {
    Props(new WSFrontend(connection, sessionRegion, session))
  }
}

class WSFrontend(val serverConnection: ActorRef, val sessionRegion: ActorRef, val session: CSession) extends HttpServiceActor with Frontend with websocket.WebSocketServerWorker {
  val transport = JsonConnection

  def businessLogic: Receive = {
    case frame: TextFrame =>
      println(s"TextFrame: $frame")
      log.info(s"Frame: ${new String(frame.payload.toArray)}")
      JsonPackageCodec.decode(frame.payload) match {
        case \/-(p) => handlePackage(p)
        case -\/(e) => sendDrop(e)
      }
    case x: FrameCommandFailed =>
      log.error(s"frame command failed: $x")
    case ResponseToClient(bs) =>
      log.info(s"ResponseToClient: $bs")
      send(TextFrame(bs))
    case ResponseToClientWithDrop(bs) =>
      send(TextFrame(bs))
      silentClose()
    case SilentClose =>
      silentClose()
  }

  def silentClose(): Unit = {
    send(CloseFrame())
    context.stop(self)
  }
}
