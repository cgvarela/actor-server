package com.secretapp.backend.sms

import akka.actor._
import dispatch._, Defaults._
import com.ning.http.client.extra.ThrottleRequestFilter
import com.typesafe.config._

import scala.collection.mutable
import scala.concurrent.duration._

trait ClickatellSmsEngine {

  val config: Config

  private val http = {
    val httpConfig              = config.getConfig("sms.clickatell.http")
    val connectionTimeoutMs     = httpConfig.getInt("connection-timeout-ms")
    val poolingConnection       = httpConfig.getBoolean("pooling-connection")
    val maximumConnectionsTotal = httpConfig.getInt("maximum-connections-total")
    val throttleRequest         = httpConfig.getInt("throttle-request")

    new Http()
      .configure(builder => {
        builder.setConnectionTimeoutInMs(connectionTimeoutMs)
        builder.setAllowPoolingConnection(poolingConnection)
        builder.setMaximumConnectionsTotal(maximumConnectionsTotal)
        builder.addRequestFilter(new ThrottleRequestFilter(throttleRequest))
        builder.build()
        builder
      })
  }

  private val svc = {
    config.checkValid(ConfigFactory.defaultReference(), "sms.clickatell")
    val user     = config.getString("sms.clickatell.user")
    val password = config.getString("sms.clickatell.password")
    val apiId    = config.getString("sms.clickatell.api-id")

    url(s"http://api.clickatell.com/http/sendmsg")
      .addHeader("Connection", "Keep-Alive")
      .addQueryParameter("user", user)
      .addQueryParameter("password", password)
      .addQueryParameter("api_id", apiId)
  }

  private def message(code: String) = s"Your secret app activation code: $code"

  def send(phoneNumber: Long, code: String): Future[String] = {
    val req = svc
      .addQueryParameter("to", phoneNumber.toString)
      .addQueryParameter("text", message(code))

    http(req OK as.String)
  }
}

class ClickatellSmsEngineActor(override val config: Config) extends Actor with ActorLogging with ClickatellSmsEngine {
  import ClickatellSmsEngineActor._

  private val ignoreIntervalInHours = {
    val clickatellConfig = config.getConfig("sms.clickatell")
    clickatellConfig.getInt("ignore-interval-hours")
  }

  private val sentCodes = new mutable.HashSet[(Long, String)]()

  private def codeWasNotSent(phoneNumber: Long, code: String) = !sentCodes.contains((phoneNumber, code))

  private def rememberSentCode(phoneNumber: Long, code: String) = sentCodes += ((phoneNumber, code))

  private def forgetSentCode(phoneNumber: Long, code: String) = sentCodes -= ((phoneNumber, code))

  private def forgetSentCodeAfterDelay(phoneNumber: Long, code: String) =
    context.system.scheduler.scheduleOnce(ignoreIntervalInHours.hours) {
      forgetSentCode(phoneNumber, code)
    }

  private def sendCode(phoneNumber: Long, code: String): Unit = {
    if (codeWasNotSent(phoneNumber, code)) {
      rememberSentCode(phoneNumber, code)
      send(phoneNumber, code)
      forgetSentCodeAfterDelay(phoneNumber, code)
    }
  }

  override def receive: Receive = {
    case Send(phoneNumber, code) => sendCode(phoneNumber, code)
  }
}

object ClickatellSmsEngineActor {
  case class Send(phoneNumber: Long, code: String)

  def apply(config: Config)(implicit system: ActorSystem): ActorRef = system.actorOf(
    Props(classOf[ClickatellSmsEngineActor], config),
    "clickatell-sms-engine"
  )

  def apply()(implicit system: ActorSystem): ActorRef = ClickatellSmsEngineActor(ConfigFactory.load())
}
