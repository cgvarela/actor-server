package com.secretapp.backend.persist

import akka.dispatch.Dispatcher
import java.util.concurrent.Executor
import scala.concurrent. { blocking, Future }
import scala.collection.JavaConversions._
import com.datastax.driver.core.{ Cluster, Session }
import com.websudos.phantom.Implicits._
import com.typesafe.config._
import scala.concurrent.ExecutionContext

object DBConnector {
  val dbConfig = ConfigFactory.load().getConfig("cassandra")

  val keySpace = dbConfig.getString("keyspace")

  val cluster =  Cluster.builder()
    .addContactPoints(dbConfig.getStringList("contact-points") :_*)
    .withPort(dbConfig.getInt("port"))
    .withoutJMXReporting()
    .withoutMetrics()
    .build()

  lazy val session = blocking {
    cluster.connect(keySpace)
  }

  def createTables(session: Session)(implicit context: ExecutionContext with Executor) = blocking {
    val fileRecord = new FileRecord()(session, context)

    Future.sequence(List(
      UserRecord.createTable(session), AuthIdRecord.createTable(session),
      SessionIdRecord.createTable(session), AuthSmsCodeRecord.createTable(session),
      PhoneRecord.createTable(session), CommonUpdateRecord.createTable(session),
      UserPublicKeyRecord.createTable(session), fileRecord.createTable(session),
      GooglePushCredentialsRecord.createTable(session), UnregisteredContactRecord.createTable(session)
    ))
  }

  def truncateTables(session: Session) = blocking {
    Future.sequence(List(
      UserRecord.truncateTable(session), AuthIdRecord.truncateTable(session),
      SessionIdRecord.truncateTable(session), AuthSmsCodeRecord.truncateTable(session),
      PhoneRecord.truncateTable(session), CommonUpdateRecord.truncateTable(session),
      UserPublicKeyRecord.truncateTable(session), GooglePushCredentialsRecord.truncateTable(session),
      UnregisteredContactRecord.truncateTable(session)
    ))
  }

//  def dumpKeySpace() = blocking {
//    session.execute(s"DESCRIBE KEYSPACE $secret;")
//  }

}

trait DBConnector {
  self: CassandraTable[_, _] =>

  def createTable(session: Session): Future[Unit] = {
    create.future()(session) map (_ => ())
  }

  def truncateTable(session: Session): Future[Unit] = {
    truncate.future()(session) map (_ => ())
  }

}
