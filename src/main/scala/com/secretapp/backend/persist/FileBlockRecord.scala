package com.secretapp.backend.persist

import akka.dispatch.Dispatcher
import com.datastax.driver.core.{ResultSet, Row, Session}
import com.secretapp.backend.data.Implicits._
import com.websudos.phantom.Implicits._
import com.websudos.phantom.keys.ClusteringOrder
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import play.api.libs.iteratee._
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scodec.bits._

case class FileBlock(blockId: Int, bytes: Array[Byte])

object FileBlockRecord {
  type EntityType = Entity[Int, FileBlock]

  val blockSize = 8 * 1024 // 8kB
}

private[persist] class FileBlockRecord(implicit session: Session, context: ExecutionContext with Executor)
    extends CassandraTable[FileBlockRecord, FileBlockRecord.EntityType] with DBConnector {
  import FileBlockRecord._

  override lazy val tableName = "file_blocks"

  object fileId extends IntColumn(this) with PartitionKey[Int] {
    override lazy val name = "file_id"
  }
  object blockId extends IntColumn(this) with ClusteringOrder[Int] with Ascending {
    override lazy val name = "block_id"
  }
  object bytes extends BlobColumn(this)
  object accessSalt extends StringColumn(this) with StaticColumn[String] {
    override lazy val name = "access_salt"
  }

  override def fromRow(row: Row): EntityType = {
    Entity(
      fileId(row),
      FileBlock(
        blockId = blockId(row),
        bytes = BitVector(bytes(row)).toByteArray
      )
    )
  }

  private val insertQuery = new ExecutablePreparedStatement {
    val query = s"INSERT INTO ${tableName} (${fileId.name}, ${blockId.name}, ${bytes.name}) VALUES (?, ?, ?);"
  }

  def insertEntity(entity: EntityType): Future[ResultSet] = {
    insertQuery.execute(
      entity.key.asInstanceOf[java.lang.Integer],
      entity.value.blockId.asInstanceOf[java.lang.Integer],
      ByteBuffer.wrap(entity.value.bytes).asReadOnlyBuffer
    )
  }

  def write(fileId: Int, offset: Int, bytes: Array[Byte]): Future[Iterator[ResultSet]] = {
    @inline def offsetValid(offset: Int) = offset >= 0 && offset % blockSize == 0

    println(s"WRITING ${offset} ${bytes.length}")
    if (!offsetValid(offset)) {
      throw new OffsetInvalid
    }

    val firstBlockId = offset / blockSize
    val finserts = bytes.grouped(blockSize).zipWithIndex map {
      case (blockBytes, i) =>
        val e = Entity(fileId, FileBlock(firstBlockId + i, blockBytes))
        println(e)
        e
    } map (insertEntity _)
    Future.sequence(finserts)
  }

  def getFileBlocks(fileId: Int, offset: Int, limit: Int): Future[Seq[ByteBuffer]] = {
    @inline def offsetValid(offset: Int) = offset >= 0 && offset % 1024 == 0
    @inline def limitValid(limit: Int) = limit > 0 && limit % 1024 == 0 && limit <= 512 * 1024

    /* TODO: LOCATION_INVALID
     *       OFFSET_TOO_LARGE
     *       FILE_LOST (wtf?)
     */
    if (!offsetValid(offset)) {
      throw new OffsetInvalid
    }

    if (!limitValid(limit)) {
      throw new LimitInvalid
    }

    val firstBlockId = Math.floor(offset.toDouble / blockSize).toInt
    val climit = Math.ceil(limit.toDouble / blockSize).toInt

    // TODO: use Iteratee
    select(_.bytes)
      .where(_.fileId eqs fileId).and(_.blockId gte firstBlockId)
      .limit(climit).fetch()
  }

  def blocksByFileId(fileId: Int): Enumerator[ByteBuffer] = {
    select(_.bytes).where(_.fileId eqs fileId).fetchEnumerator
  }
}