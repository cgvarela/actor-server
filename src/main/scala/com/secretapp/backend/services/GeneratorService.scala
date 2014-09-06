package com.secretapp.backend.services

import com.secretapp.backend.services.common.RandomService

trait GeneratorService extends RandomService {
  def genNewAuthId = rand.nextLong

  def genSmsCode = rand.nextLong().toString.drop(1).take(6)

  def genSmsHash = rand.nextLong().toString

  def genUserId = rand.nextInt // TODO: akka service for ID's

  def genFileId = rand.nextInt

  def genUserAccessSalt = rand.nextString(30)

  def genFileAccessSalt = rand.nextString(30)
}
