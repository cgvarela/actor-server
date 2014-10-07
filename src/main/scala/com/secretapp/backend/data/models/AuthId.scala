package com.secretapp.backend.data.models

import com.secretapp.backend.persist.KeyedEntity
import scala.concurrent.Future
import com.datastax.driver.core.Session
import com.secretapp.backend.persist._

@SerialVersionUID(1l)
case class AuthId(authId: Long, userId: Option[Int]) extends KeyedEntity[Long] {
  val key = authId

  def user(implicit session: Session, ex: scala.concurrent.ExecutionContext): Future[Option[User]] = {
    userId match {
      case Some(uid) => UserRecord.getEntity(uid, authId)
      case None => Future.successful(None)
    }
  }
}
