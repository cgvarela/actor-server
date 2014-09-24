package com.secretapp.backend.data.message

import scala.collection.immutable
import com.secretapp.backend.data.transport.MessageBox

case class Container(messages: immutable.Seq[MessageBox]) extends TransportMessage
object Container extends TransportMessageMessageObject {
  val header = 0xa
}
