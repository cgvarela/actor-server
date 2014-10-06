package com.secretapp.backend.data.json.message.rpc.file

import com.secretapp.backend.data.json._
import com.secretapp.backend.data.json.JsonSpec
import com.secretapp.backend.data.json.JsonSpec._
import com.secretapp.backend.data.message.rpc.RpcRequestMessage
import com.secretapp.backend.data.message.rpc.file._
import play.api.libs.json._
import JsonFormatsSpec._
import scala.util.Random

class JsonFormatsSpec extends JsonSpec {

  "(de)serializer" should {

    "(de)serialize UploadConfig" in {
      val (v, j) = genUploadConfig
      testToAndFromJson[UploadConfig](j, v)
    }

    "(de)serialize FileLocation" in {
      val (v, j) = genFileLocation
      testToAndFromJson[FileLocation](j, v)
    }

  }

  "RpcRequestMessage (de)serializer" should {

    "(de)serialize RequestCompleteUpload" in {
      val (uploadConfig, uploadConfigJson) = genUploadConfig
      val v = RequestCompleteUpload(uploadConfig, 1, 2)
      val j = withHeader(RequestCompleteUpload.requestType)(
        "config" -> uploadConfigJson,
        "blockCount" -> 1,
        "crc32" -> "2"
      )
      testToAndFromJson[RpcRequestMessage](j, v)
    }

    "(de)serialize RequestGetFile" in {
      val (fileLocation, fileLocationJson) = genFileLocation
      val v = RequestGetFile(fileLocation, 1, 2)
      val j = withHeader(RequestGetFile.requestType)(
        "fileLocation" -> fileLocationJson,
        "offset" -> 1,
        "limit" -> 2
      )
      testToAndFromJson[RpcRequestMessage](j, v)
    }

    "(de)serialize RequestStartUpload" in {
      val v = RequestStartUpload()
      val j = withHeader(RequestStartUpload.requestType)()
      testToAndFromJson[RpcRequestMessage](j, v)
    }

    "(de)serialize RequestUploadPart" in {
      val (uploadConfig, uploadConfigJson) = genUploadConfig
      val (bitVector, bitVectorJson) = genBitVector
      val v = RequestUploadPart(uploadConfig, 1, bitVector)
      val j = withHeader(RequestUploadPart.requestType)(
        "config" -> uploadConfigJson,
        "offset" -> 1,
        "data" -> bitVectorJson
      )
      testToAndFromJson[RpcRequestMessage](j, v)
    }

  }
}

object JsonFormatsSpec {

  def genUploadConfig = {
    val (bitVector, bitVectorJson) = genBitVector

    (
      UploadConfig(bitVector),
      Json.obj(
        "serverData" -> bitVectorJson
      )
    )
  }

  def genFileLocation = {
    val fileId = Random.nextLong()
    val accessHash = Random.nextLong()

    (
      FileLocation(fileId, accessHash),
      Json.obj(
        "fileId" -> fileId.toString,
        "accessHash" -> accessHash.toString
      )
    )
  }

}