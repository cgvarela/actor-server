package com.secretapp.backend.persist

import com.websudos.phantom.Implicits._
import com.secretapp.backend.models
import im.actor.server.persist.unit.SqlSpec
import org.specs2.mutable.Specification
import org.specs2.matcher.NoConcurrentExecutionContext
import scalaz._
import Scalaz._

class AuthIdSpec extends Specification with SqlSpec with NoConcurrentExecutionContext {
  "AuthIdRecord" should {
    "insert/get AuthId Entity" in new sqlDb {
      val entity = models.AuthId(123L, None)
      val insertFuture = AuthId.create(entity.id, entity.userId)

      val chain = for {
        insertDone <- insertFuture
        oneSelect <- AuthId.find(entity.id)
      } yield oneSelect

      chain must be_== (entity.some).await
    }
  }
}
