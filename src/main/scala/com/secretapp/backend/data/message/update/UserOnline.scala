package com.secretapp.backend.data.message.update

@SerialVersionUID(1l)
case class UserOnline(uid: Int) extends WeakUpdateMessage {
  val weakUpdateHeader = UserOnline.weakUpdateHeader
}

object UserOnline extends WeakUpdateMessageObject {
  val weakUpdateHeader = 0x07
}
