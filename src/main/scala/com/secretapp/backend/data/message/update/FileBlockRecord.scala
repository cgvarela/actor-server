package com.secretapp.backend.persist

import akka.actor.ActorSystem
import akka.dispatch.Dispatcher
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.{ ResultSet, Row, Session }
import com.newzly.phantom.Implicits._
import com.secretapp.backend.data.Implicits._
import com.secretapp.backend.data.models._
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scodec.bits._

case class FileBlock(blockId: Int, bytes: Array[Byte])

object FileBlockRecord {
  type EntityType = Entity[Int, FileBlock]

  val blockSize = 8 * 1024 // 8kB
}

sealed class Error extends Exception
class LocationInvalid extends Error
class OffsetInvalid extends Error
class OffsetTooLarge extends Error
class LimitInvalid extends Error
class FileLost extends Error

class FileBlockRecord(implicit session: Session, context: ExecutionContext with Executor)
    extends CassandraTable[FileBlockRecord, FileBlockRecord.EntityType] with DBConnector {
  import FileBlockRecord._

  override lazy val tableName = "file_blocks"

  object fileId extends IntColumn(this) with PartitionKey[Int] {
    override lazy val name = "file_id"
  }
  object blockId extends IntColumn(this) with PrimaryKey[Int] {
    override lazy val name = "block_id"
  }
  object bytes extends BigIntColumn(this)

  override def fromRow(row: Row): EntityType = {
    Entity(
      fileId(row),
      FileBlock(
        blockId = blockId(row),
        bytes = bytes(row).toByteArray
      )
    )
  }

  val insertQuery = new ExecutablePreparedStatement {
    val query = s"INSERT INTO ${tableName} (${fileId.name}, ${blockId.name}, ${bytes.name}) VALUES (?, ?, ?);"
  }

  def insertEntity(entity: EntityType): Future[ResultSet] = {
    insertQuery.execute(
      entity.key.asInstanceOf[java.lang.Integer],
      entity.value.blockId.asInstanceOf[java.lang.Integer],
      new java.math.BigInteger(entity.value.bytes)
    )
  }

  def write(fileId: Int, offset: Int, bytes: Array[Byte]): Future[Iterator[ResultSet]] = {
    @inline def offsetValid(offset: Int) = offset >= 0 && offset % blockSize == 0

    if (!offsetValid(offset)) {
      throw new OffsetInvalid
    }

    val firstBlockId = offset / blockSize
    val finserts = bytes.grouped(blockSize).zipWithIndex map {
      case (blockBytes, i) =>
        Entity(fileId, FileBlock(firstBlockId + i, blockBytes))
    } map (insertEntity _)
    Future.sequence(finserts)
  }

  def getFile(fileId: Int, offset: Int, limit: Int): Future[Array[Byte]] = {
    @inline def offsetValid(offset: Int) = offset >= 0 && offset % 1024 == 0
    @inline def limitValid(limit: Int) = limit > 0 || limit % 1024 == 0 && limit <= 512 * 1024

    /* TODO: LOCATION_INVALID
     *       OFFSET_TOO_LARGE
     *       FILE_LOST (wtf?)
     */
    if (!offsetValid(offset)) {
      throw new OffsetInvalid
    } else if (!limitValid(limit)) {
      throw new LimitInvalid
    }

    val firstBlockId = Math.floor(offset.toDouble / blockSize).toInt
    val climit = Math.ceil(limit.toDouble / blockSize).toInt

    // TODO: use Iteratee
    for {
      blocks <- select(_.bytes)
      .where(_.fileId eqs fileId).and(_.blockId gte firstBlockId)
      .limit(climit).fetch()
    } yield {
      val bytes = blocks.foldLeft(Array[Byte]())(_ ++ _.toByteArray)
      bytes.drop(offset % blockSize).take(limit)
    }
  }


}