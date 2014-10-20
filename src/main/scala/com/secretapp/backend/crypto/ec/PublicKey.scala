package com.secretapp.backend.crypto.ec

import org.bouncycastle.jce.provider.BouncyCastleProvider
import scodec.bits.BitVector
import java.security.{ Security, MessageDigest }
import scala.annotation.tailrec

object PublicKey {
  Security.addProvider(new BouncyCastleProvider())

  def keyHash(pk: BitVector): Long = {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf: Array[Byte] = digest.digest(pk.toByteArray)
    val longSize = 8

    @tailrec
    def f(res: Long, index: Int): Long = {
      if (index == longSize) res
      else {
        val n = buf(index) ^ buf(index + longSize) ^ buf(index + longSize * 2) ^ buf(index + longSize * 3)
        f(((n & 0xffL) << (longSize - index - 1) * longSize) | res, index + 1)
      }
    }
    f(0L, 0)
  }
}
