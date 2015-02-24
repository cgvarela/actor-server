package com.secretapp.backend.models

import scala.collection.immutable

object AvatarData {
  def empty: AvatarData = AvatarData(
    smallAvatarFileId = None,
    smallAvatarFileHash = None,
    smallAvatarFileSize = None,
    largeAvatarFileId = None,
    largeAvatarFileHash = None,
    largeAvatarFileSize = None,
    fullAvatarFileId = None,
    fullAvatarFileHash = None,
    fullAvatarFileSize = None,
    fullAvatarWidth = None,
    fullAvatarHeight = None
  )
}

case class AvatarData(
  smallAvatarFileId: Option[Long],
  smallAvatarFileHash: Option[Long],
  smallAvatarFileSize: Option[Int],
  largeAvatarFileId: Option[Long],
  largeAvatarFileHash: Option[Long],
  largeAvatarFileSize: Option[Int],
  fullAvatarFileId: Option[Long],
  fullAvatarFileHash: Option[Long],
  fullAvatarFileSize: Option[Int],
  fullAvatarWidth: Option[Int],
  fullAvatarHeight: Option[Int]
) {
  lazy val smallAvatarImage =
    for (
      id <- smallAvatarFileId;
      hash <- smallAvatarFileHash;
      size <- smallAvatarFileSize
    ) yield AvatarImage(FileLocation(id, hash), 100, 100, size)

  lazy val largeAvatarImage =
    for (
      id <- largeAvatarFileId;
      hash <- largeAvatarFileHash;
      size <- largeAvatarFileSize
    ) yield AvatarImage(FileLocation(id, hash), 200, 200, size)

  lazy val fullAvatarImage =
    for (
      id <- fullAvatarFileId;
      hash <- fullAvatarFileHash;
      size <- fullAvatarFileSize;
      w <- fullAvatarWidth;
      h <- fullAvatarHeight
    ) yield AvatarImage(FileLocation(id, hash), w, h, size)

  lazy val avatar =
    if (immutable.Seq(smallAvatarImage, largeAvatarImage, fullAvatarImage).exists(_.isDefined))
      Some(Avatar(smallAvatarImage, largeAvatarImage, fullAvatarImage))
    else
      None
}
