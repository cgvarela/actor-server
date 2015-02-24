package com.secretapp.backend.api.frontend.tcp

import akka.actor._
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress

object TcpServer {
  def props(sessionRegion: ActorRef) = Props(new TcpServer(sessionRegion))
}

class TcpServer(sessionRegion: ActorRef) extends Actor with ActorLogging {
  import akka.io.Tcp._
  import context.system

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def preStart(): Unit = {
    IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 0))
  }

  override def postRestart(thr: Throwable): Unit = context.stop(self)

  def receive = {
    case CommandFailed(_: Bind) =>
      //log.debug("CommandFailed")
      context.stop(self)
    case c @ Connected(remote, local) =>
      log.debug(s"Connected: $c")
      val connection = sender()
      val frontend = context.actorOf(TcpFrontend.props(connection, remote, sessionRegion))
      connection ! Register(frontend, keepOpenOnPeerClosed = true)
  }
}
