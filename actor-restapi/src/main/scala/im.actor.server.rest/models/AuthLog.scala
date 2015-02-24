package im.actor.server.rest.models

import com.secretapp.backend.persist._
import com.secretapp.backend.persist.events.LogEvent
import org.joda.time.DateTime
import spray.json._
import scala.concurrent.{ExecutionContext, Future}

case class AuthLog(id: Long, authId: Long, phoneNumber: Long, email: String,
                       userId: Int, userName: String, deviceHash: String, deviceTitle: String,
                       klass: Int, jsonBody: String, createdAt: DateTime)

object AuthLog extends DefaultJsonProtocol {
  implicit object AuthLogItemWrites extends RootJsonWriter[AuthLog] {
    def write(e: AuthLog) = JsObject(
      "id" -> JsNumber(e.id),
      "authId" -> JsString(e.authId.toString),
      "phoneNumber" -> JsString(e.phoneNumber.toString),
      "email" -> JsString(e.email),
      "userId" -> JsNumber(e.userId),
      "userName" -> JsString(e.userName),
      "deviceHash" -> JsString(e.deviceHash),
      "deviceTitle" -> JsString(e.deviceTitle),
      "klass" -> JsNumber(e.klass),
      "body" -> e.jsonBody.parseJson,
      "createdAt" -> JsString(e.createdAt.toDateTimeISO.toString)
    )
  }

  def paginate(req: Map[String, String] = Map())
              (implicit ec: ExecutionContext): Future[(Seq[AuthLog], Int)] =
  {
    for {
      (logEvents, totalCount) <- LogEvent.all(req)
      authSessions <- AuthSession.getDevisesData(logEvents.map(_.authId))
      userNames <- User.getNames(authSessions.map(_.userId))
    } yield {
      val authSessionsMap = authSessions.map { s => (s.authId, s) }.toMap
      val userNamesMap = userNames.toMap
      val authLogs = logEvents.map { e =>
        val s = authSessionsMap.get(e.authId)
        val userId = s.map(_.userId).getOrElse(0)
        AuthLog(
          id = e.id,
          authId = e.authId,
          phoneNumber = e.phoneNumber,
          email = e.email,
          userId = userId,
          userName = userNamesMap.getOrElse(userId, ""),
          deviceHash = s.map(_.deviceHash).getOrElse(""),
          deviceTitle = s.map(_.deviceTitle).getOrElse(""),
          klass = e.klass,
          jsonBody = e.jsonBody,
          createdAt = e.createdAt
        )
      }
      (authLogs, totalCount)
    }
  }
}
