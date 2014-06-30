package com.secretapp.backend.protocol.codecs

import scodec.bits.BitVector
import org.scalacheck._
import org.scalacheck.Prop._
import scalaz._
import Scalaz._

object VarIntSpecification extends Properties("VarInt") {

  val integers = Gen.choose(Int.MinValue, Int.MaxValue)

  property("encode/decode") = forAll(integers) { (a: Int) =>
    VarInt.decode(VarInt.encode(a).toOption.get) == (BitVector.empty, a.abs).right
  }

}
