package com.secretapp.backend.protocol.codecs.message.update

import scala.util.{ Try, Success, Failure }
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.data.message.update.contact._
import com.secretapp.backend.protocol.codecs.message.update.contact._
import scodec.bits.BitVector
import scalaz._
import Scalaz._

object SeqUpdateMessageCodec {
  def encode(body: SeqUpdateMessage): String \/ BitVector = {
    body match {
      case m: Message           => MessageCodec.encode(m)
      case m: EncryptedMessage  => EncryptedMessageCodec.encode(m)
      case m: MessageSent       => MessageSentCodec.encode(m)
      case n: NewDevice         => NewDeviceCodec.encode(n)
      case n: RemovedDevice     => RemovedDeviceCodec.encode(n)
      case u: UserAvatarChanged => UserAvatarChangedCodec.encode(u)
      case u: NameChanged       => NameChangedCodec.encode(u)
      case u: EncryptedReceived => EncryptedReceivedCodec.encode(u)
      case u: EncryptedRead     => EncryptedReadCodec.encode(u)
      case u: EncryptedReadByMe => EncryptedReadByMeCodec.encode(u)
      case u: MessageReceived   => MessageReceivedCodec.encode(u)
      case u: MessageRead       => MessageReadCodec.encode(u)
      case u: MessageReadByMe   => MessageReadByMeCodec.encode(u)
      case u: MessageDelete     => MessageDeleteCodec.encode(u)
      case u: GroupInvite       => GroupInviteCodec.encode(u)
      case u: GroupUserAdded    => GroupUserAddedCodec.encode(u)
      case u: GroupUserLeave    => GroupUserLeaveCodec.encode(u)
      case u: GroupUserKick     => GroupUserKickCodec.encode(u)
      case u: GroupTitleChanged => GroupTitleChangedCodec.encode(u)
      case u: GroupAvatarChanged=> GroupAvatarChangedCodec.encode(u)
      case u: GroupMembersUpdate=> GroupMembersUpdateCodec.encode(u)
      case u: ContactRegistered => ContactRegisteredCodec.encode(u)
      case u: ContactsAdded     => ContactsAddedCodec.encode(u)
      case u: ContactsRemoved   => ContactsRemovedCodec.encode(u)
      case u: LocalNameChanged  => LocalNameChangedCodec.encode(u)
      case u: ChatDelete        => ChatDeleteCodec.encode(u)
      case u: ChatClear         => ChatClearCodec.encode(u)
      case u: PhoneTitleChanged => PhoneTitleChangedCodec.encode(u)
    }
  }

  def decode(commonUpdateHeader: Int, buf: BitVector): String \/ SeqUpdateMessage = {
    val tried = Try(commonUpdateHeader match {
      case Message.header           => MessageCodec.decode(buf)
      case EncryptedMessage.header  => EncryptedMessageCodec.decode(buf)
      case MessageSent.header       => MessageSentCodec.decode(buf)
      case NewDevice.header         => NewDeviceCodec.decode(buf)
      case RemovedDevice.header     => RemovedDeviceCodec.decode(buf)
      case UserAvatarChanged.header => UserAvatarChangedCodec.decode(buf)
      case NameChanged.header       => NameChangedCodec.decode(buf)
      case EncryptedReceived.header => EncryptedReceivedCodec.decode(buf)
      case EncryptedRead.header     => EncryptedReadCodec.decode(buf)
      case EncryptedReadByMe.header => EncryptedReadByMeCodec.decode(buf)
      case MessageReceived.header   => MessageReceivedCodec.decode(buf)
      case MessageRead.header       => MessageReadCodec.decode(buf)
      case MessageReadByMe.header   => MessageReadByMeCodec.decode(buf)
      case GroupInvite.header       => GroupInviteCodec.decode(buf)
      case GroupUserAdded.header    => GroupUserAddedCodec.decode(buf)
      case GroupUserLeave.header    => GroupUserLeaveCodec.decode(buf)
      case GroupUserKick.header     => GroupUserKickCodec.decode(buf)
      case GroupTitleChanged.header => GroupTitleChangedCodec.decode(buf)
      case GroupAvatarChanged.header=> GroupAvatarChangedCodec.decode(buf)
      case GroupMembersUpdate.header=> GroupMembersUpdateCodec.decode(buf)
      case ContactRegistered.header => ContactRegisteredCodec.decode(buf)
      case ContactsAdded.header     => ContactsAddedCodec.decode(buf)
      case ContactsRemoved.header   => ContactsRemovedCodec.decode(buf)
      case LocalNameChanged.header  => LocalNameChangedCodec.decode(buf)
      case ChatDelete.header        => ChatDeleteCodec.decode(buf)
      case ChatClear.header         => ChatClearCodec.decode(buf)
      case PhoneTitleChanged.header => PhoneTitleChangedCodec.decode(buf)
    })
    tried match {
      case Success(res) => res match {
        case \/-(r) => r._2.right
        case l@(-\/(_)) => l
      }
      case Failure(e) => e.getMessage.left
    }
  }
}
