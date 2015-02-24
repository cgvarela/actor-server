package com.secretapp.backend.data.message.struct

import com.secretapp.backend.data.message.ProtobufMessage
import im.actor.messenger.{ api => protobuf }
import com.secretapp.backend.models

@SerialVersionUID(1L)
case class AuthSession(
  id: Int, authHolder: Int, appId: Int, appTitle: String, deviceTitle: String, authTime: Int,
  authLocation: String, latitude: Option[Double], longitude: Option[Double]
) extends ProtobufMessage {
  def toProto = protobuf.AuthSession(
    id, authHolder, appId, appTitle, deviceTitle, authTime,
    authLocation, latitude, longitude
  )
}

object AuthSession {
  def fromProto(authItem: protobuf.AuthSession): AuthSession =
    AuthSession(authItem.id, authItem.authHolder, authItem.appId, authItem.appTitle, authItem.deviceTitle, authItem.authTime,
      authItem.authLocation, authItem.latitude, authItem.longitude)

  def fromModel(a: models.AuthSession, currentAuthId: Long) = {
    val authHolder = if (currentAuthId == a.authId) 0 else 1

    AuthSession(
      a.id, authHolder, a.appId, a.appTitle, a.deviceTitle, (a.authTime.getMillis / 1000).toInt,
      a.authLocation, a.latitude, a.longitude
    )
  }
}
