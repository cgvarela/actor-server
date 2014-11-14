package com.secretapp.backend.services.rpc.messaging

import akka.actor._
import akka.pattern.ask
import com.datastax.driver.core.{ Session => CSession }
import com.secretapp.backend.api.{ SocialProtocol, UpdatesBroker }
import com.secretapp.backend.data.message.struct
import com.secretapp.backend.data.message.rpc.messaging._
import com.secretapp.backend.data.message.rpc.{ Error, Ok, RpcResponse, ResponseAvatarChanged, ResponseVoid }
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.data.message.{ update => updateProto }
import com.secretapp.backend.models
import com.secretapp.backend.helpers._
import com.secretapp.backend.persist
import com.secretapp.backend.services.common.RandomService
import com.secretapp.backend.util.{ AvatarUtils }
import java.util.UUID
import scala.collection.immutable
import scala.concurrent.Future
import scala.language.postfixOps
import scalaz.Scalaz._
import scalaz._
import scodec.bits._

trait MessagingHandlers extends RandomService with UserHelpers with GroupHelpers with PeerHelpers with UpdatesHelpers {
  self: Handler =>

  import context.{ dispatcher, system }
  import UpdatesBroker._

  implicit val session: CSession

  val handleMessaging: RequestMatcher = {
    case RequestSendMessage(peer, randomId, message) =>
      handleRequestSendMessage(peer, randomId, message)
    case RequestSendEncryptedMessage(outPeer, randomId, encryptedMessage, keys, ownKeys) =>
      handleRequestSendEncryptedMessage(outPeer, randomId, encryptedMessage, keys, ownKeys)
    case RequestEncryptedReceived(outPeer, randomId) =>
      handleRequestEncryptedReceived(outPeer, randomId)
    case RequestEncryptedRead(outPeer, randomId) =>
      handleRequestEncryptedRead(outPeer, randomId)
    case RequestMessageReceived(outPeer, randomId) =>
      handleRequestMessageReceived(outPeer, randomId)
    case RequestMessageRead(outPeer, randomId) =>
      handleRequestMessageRead(outPeer, randomId)
  }

  protected def handleRequestSendMessage(
    outPeer: struct.OutPeer,
    randomId: Long,
    message: MessageContent
  ): Future[RpcResponse] = {
    withOutPeer(outPeer, currentUser) {
      val date = System.currentTimeMillis()

      val selfUpdateFuture = outPeer.typ match {
        case struct.PeerType.Private =>
          val myAuthIdsFuture   = getAuthIds(currentUser.uid)
          val peerAuthIdsFuture = getAuthIds(outPeer.id)

          for {
            myAuthIds <- myAuthIdsFuture
            peerAuthIds <- peerAuthIdsFuture
          } yield {
            val update = updateProto.Message(
              peer = struct.Peer.privat(currentUser.uid),
              senderUid = currentUser.uid,
              date = date,
              randomId = randomId,
              message = message
            )

            (peerAuthIds ++
              (myAuthIds filterNot (_ == currentUser.authId))
            ) foreach { authId =>
              updatesBrokerRegion ! NewUpdatePush(authId, update)
            }
          }

          Future.successful(
            updateProto.MessageSent(
              outPeer.asPeer,
              randomId,
              date
            )
          )
        case struct.PeerType.Group =>
          getGroupUserAuthIds(outPeer.id) map { groupAuthIds =>
            groupAuthIds foreach { authId =>
              if (authId != currentUser.authId) {
                writeNewUpdate(
                  authId,
                  updateProto.Message(
                    peer = struct.Peer.group(outPeer.id),
                    senderUid = currentUser.uid,
                    date = date,
                    randomId = randomId,
                    message = message
                  )
                )
              }
            }

            updateProto.MessageSent(
              outPeer.asPeer,
              randomId,
              date
            )
          }
      }

      selfUpdateFuture flatMap {
        withNewUpdateState(
          currentUser.authId,
          _
        ) { s =>
          val rsp = ResponseMessageSent(seq = s._1, state = Some(s._2), date)
          Ok(rsp)
        }
      }
    }
  }

  protected def handleRequestSendEncryptedMessage(
    outPeer: struct.OutPeer,
    randomId: Long,
    encryptedMessage: BitVector,
    keys: immutable.Seq[EncryptedAESKey],
    ownKeys: immutable.Seq[EncryptedAESKey]
  ): Future[RpcResponse] = {
    val outUserId = outPeer.id

    if (outPeer.typ == struct.PeerType.Group) {
      throw new Exception("Encrypted group sending is not implemented yet.")
    }

    val ownPairsEFuture = fetchAuthIdsForValidKeys(currentUser.uid, ownKeys)
    val outPairsEFuture = fetchAuthIdsForValidKeys(outUserId, keys)

    val pairsEFuture = for {
      ownPairsE <- ownPairsEFuture
      outPairsE <- outPairsEFuture
    } yield {
      ownPairsE +++ outPairsE
    }

    withOutPeer(outPeer, currentUser) {
      // Record relation between receiver authId and sender uid
      socialBrokerRegion ! SocialProtocol.SocialMessageBox(
        currentUser.uid, SocialProtocol.RelationsNoted(Set(outUserId)))

      pairsEFuture flatMap {
        case \/-(pairs) =>
          // TODO: get time from an actor with PinnedDispatcher
          val date = System.currentTimeMillis()

          pairs foreach {
            case (EncryptedAESKey(keyHash, aesEncryptedKey), authId) =>
              val update = NewUpdatePush(
                authId,
                updateProto.EncryptedMessage(
                  peer = struct.Peer(struct.PeerType.Private, currentUser.uid),
                  senderUid = currentUser.uid,
                  keyHash = keyHash,
                  aesEncryptedKey = aesEncryptedKey,
                  message = encryptedMessage,
                  date = date
                )
              )

              updatesBrokerRegion ! update
          }

          withNewUpdateState(
            currentUser.authId,
            updateProto.MessageSent(
              outPeer.asPeer,
              randomId,
              date
            )
          ) { s =>
            val rsp = ResponseSeq(seq = s._1, state = Some(s._2))
            Ok(rsp)
          }
        case -\/((newKeys, removedKeys, invalidKeys)) =>
          val errorData = struct.WrongReceiversErrorData(newKeys.toSeq, removedKeys.toSeq, invalidKeys.toSeq)

          Future.successful(
            Error(400, "WRONG_KEYS", "", false, Some(errorData))
          )
      }
    }
  }

  protected def handleRequestEncryptedReceived(
    outPeer: struct.OutPeer,
    randomId: Long
  ): Future[RpcResponse] = {
    withOutPeer(outPeer, currentUser) {
      outPeer.typ match {
        case struct.PeerType.Private =>
          for {
            authIds <- getAuthIds(outPeer.id)
          } yield {
            authIds foreach { authId =>
              updatesBrokerRegion ! NewUpdatePush(authId, updateProto.EncryptedReceived(outPeer.asPeer, randomId))
            }
          }
          Future.successful(Ok(ResponseVoid()))
        case struct.PeerType.Group =>
          Future.successful(Error(400, "NOT_IMPLEMENTED", "Group encrypted messaging is not implemented yet.", false))
      }
    }
  }

  protected def handleRequestEncryptedRead(
    outPeer: struct.OutPeer,
    randomId: Long
  ): Future[RpcResponse] = {
    withOutPeer(outPeer, currentUser) {
      outPeer.typ match {
        case struct.PeerType.Private =>
          val peerAuthIdsFuture = getAuthIds(outPeer.id)
          val myAuthIdsFuture   = getAuthIds(currentUser.uid)

          for {
            peerAuthIds <- peerAuthIdsFuture
            myAuthIds   <- myAuthIdsFuture
          } yield {
            peerAuthIds foreach { authId =>
              updatesBrokerRegion ! NewUpdatePush(authId, updateProto.EncryptedRead(outPeer.asPeer, randomId))
            }

            myAuthIds foreach { authId =>
              updatesBrokerRegion ! NewUpdatePush(authId, updateProto.EncryptedReadByMe(outPeer.asPeer, randomId))
            }
          }
          Future.successful(Ok(ResponseVoid()))
        case struct.PeerType.Group =>
          Future.successful(Error(400, "NOT_IMPLEMENTED", "Group encrypted messaging is not implemented yet.", false))
      }
    }
  }

  protected def handleRequestMessageReceived(
    outPeer: struct.OutPeer,
    randomId: Long
  ): Future[RpcResponse] = ???

  protected def handleRequestMessageRead(
    outPeer: struct.OutPeer,
    date: Long
  ): Future[RpcResponse] = ???

  /*

  protected def handleRequestSendEncryptedMessage(
    peer: struct.OutPeer,
    randomId: Long,
    message: MessageContent
  ): Future[RpcResponse] = {
    /**
      * Makes updates for valid keys
      *
      * @return Right containing sequences of (authId, key) or Left containing new keys, removed keys and invalid keys
      */
    def mkUpdates(): Future[(Seq[struct.UserKey], Seq[struct.UserKey], Seq[struct.UserKey]) \/ Seq[NewUpdatePush]] = {
      val fown  = fetchAuthIdsAndCheckKeysFor(currentUser.uid, message.ownKeys, Some(currentUser.publicKeyHash))
      val fdest = fetchAuthIdsAndCheckKeysFor(destUserId, message.keys, None)

      for {
        own <- fown
        dest <- fdest
      } yield {
        (
          own._2 ++ dest._2, // new
          own._3 ++ dest._3, // removed
          own._4 ++ dest._4  // invalid
        ) match {
          case (Nil, Nil, Nil) =>
            val authIdsKeys = own._1 ++ dest._1

            val updates = authIdsKeys map {
              case (authId, key) =>
                NewUpdatePush(
                  authId,
                  updateProto.Message(
                    currentUser.uid,
                    destUserId,
                    MessageContent(
                      key.keyHash,
                      key.aesEncryptedKey,
                      message.encryptedMessage
                    )
                  )
                )
            }
            updates.right
          case res @ (newKeys, removedKeys, invalidKeys) => res.left
        }
      }
    }

    withUser(destUserId, accessHash, currentUser) { destUserEntity =>
      // Record relation between receiver authId and sender uid
      socialBrokerRegion ! SocialProtocol.SocialMessageBox(
        currentUser.uid, SocialProtocol.RelationsNoted(Set(destUserId)))

      mkUpdates() flatMap {
        case \/-(updates) =>
          updates foreach { update =>
            updatesBrokerRegion ! update
          }

          val fstate = ask(
            updatesBrokerRegion,
            NewUpdatePush(
              currentUser.authId,
              updateProto.MessageSent(destUserId, randomId, System.currentTimeMillis())
            )).mapTo[UpdatesBroker.StrictState]

          for {
            s <- fstate
          } yield {
            val rsp = ResponseSeq(seq = s._1, state = Some(s._2))
            Ok(rsp)
          }
        case -\/((newKeys, removedKeys, invalidKeys)) =>
          val errorData = WrongReceiversErrorData(newKeys, removedKeys, invalidKeys)

          Future.successful(
            Error(400, "WRONG_KEYS", "", false, Some(errorData))
          )
      }
    }
  }

  def mkInvites(
    group: models.Group,
    keys: EncryptedUserAESKeys,
    encryptedMessage: BitVector,
    groupUserIds: immutable.Seq[Int]
    ): Future[Seq[Error \/ NewUpdatePush]] = {
      val futures = keys.keys map { encryptedAESKey =>
        persist.UserPublicKey.getAuthIdAndSalt(keys.userId, encryptedAESKey.keyHash) flatMap {
          case Some((authId, accessSalt)) =>
            if (ACL.userAccessHash(currentUser.authId, keys.userId, accessSalt) != keys.accessHash) {
              Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false).left)
            } else {
              for {
                users <- Future.sequence(groupUserIds map (getUserIdStruct(_, authId))) map (_.flatten)
              } yield {
                NewUpdatePush(
                  authId,
                  updateProto.GroupInvite(
                    group.id,
                    group.accessHash,
                    group.creatorUserId,
                    currentUser.uid,
                    group.title,
                    users,
                    EncryptedRSAPackage(
                      keyHash = encryptedAESKey.keyHash,
                      aesEncryptedKey = encryptedAESKey.aesEncryptedKey,
                      message = encryptedMessage
                    )
                  )
                ).right
              }
            }
          case None =>
            Future.successful(
              Error(404, "USER_NOT_FOUND", s"User ${keys.userId} with ${encryptedAESKey.keyHash} not found", false).left
            )
        }
      }

      Future.sequence(futures)
    }

  // TODO: don't allow to add with the same keyhash user twice
  protected def handleRequestInviteUser(
    groupId: Int, accessHash: Long, randomId: Long, groupKeyHash: BitVector, broadcast: EncryptedRSABroadcast
  ): Future[RpcResponse] = {
    val fgroupUserIds = persist.GroupUser.getUsers(groupId)
    persist.Group.getEntity(groupId) flatMap { optGroup =>
      optGroup map { group =>
        if (group.accessHash != accessHash) {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false))
        } else if (group.keyHash != groupKeyHash) {
          Future.successful(Error(400, "WRONG_KEY", "Invalid group key hash.", false))
        } else {
          val f = for {
            groupUserIds <- fgroupUserIds
            groupAuthIds <- Future.sequence(groupUserIds map getAuthIds) map (_.flatten)
            einvites <- Future.sequence(broadcast.keys map (mkInvites(group, _, broadcast.encryptedMessage, groupUserIds.toVector))) map (_.flatten)
          } yield {
            (einvites, groupAuthIds)
          }

          f flatMap {
            case (einvites, groupAuthIds) =>
              einvites.toVector.sequenceU match {
                case -\/(e) => Future.successful(e)
                case \/-(updates) =>
                  val newUserIds = broadcast.keys map (key => (key.userId, key.keys map (_.keyHash) toSet))

                  Future.sequence(newUserIds map {
                    case (userId, keyHashes) =>
                      persist.GroupUser.addUser(groupId, userId, keyHashes) map {
                        case -\/(_) => userId.left
                        case \/-(_) => userId.right
                      }
                  }) map (_.toVector.separate) flatMap {
                    case (_, addedUsers) =>
                      updates foreach { update =>
                        updatesBrokerRegion ! update
                      }

                      groupAuthIds foreach {
                        case currentUser.authId =>
                        case authId =>
                          addedUsers foreach { userId =>
                            updatesBrokerRegion ! NewUpdatePush(authId, updateProto.GroupUserAdded(groupId, userId, currentUser.uid))
                          }
                      }

                      for {
                        xs <- Future.sequence(addedUsers map { userId =>
                          ask(updatesBrokerRegion, NewUpdatePush(currentUser.authId, updateProto.GroupUserAdded(groupId, userId, currentUser.uid))).mapTo[StrictState] map {
                            case (seq, state) => (seq, Some(state))
                          }
                        }) flatMap {
                          case xs if xs.isEmpty =>
                            getState(currentUser.authId) map (Seq(_))
                          case xs => Future.successful(xs)
                        }
                      } yield {
                        val s = xs.maxBy(_._1)
                        Ok(ResponseSeq(s._1, s._2))
                      }
                  }
              }
          }
        }
      } getOrElse Future.successful(Error(404, "GROUP_DOES_NOT_EXISTS", "Group does not exists.", true))
    }
  }

  protected def handleRequestCreateGroup(
    randomId: Long,
    title: String,
    keyHash: BitVector,
    publicKey: BitVector,
    broadcast: EncryptedRSABroadcast
  ): Future[RpcResponse] = {
    val id = rand.nextInt()
    val group = models.Group(id, currentUser.uid, rand.nextLong(), title, keyHash, publicKey)

    val newUserIds = broadcast.keys map (key => (key.userId, key.keys.map(_.keyHash).toSet))

    persist.Group.insertEntity(group) flatMap { _ =>
      Future.sequence(broadcast.keys map (mkInvites(group, _, broadcast.encryptedMessage, (newUserIds map (_._1)) :+ currentUser.uid))) map (_.flatten) flatMap { einvites =>
        einvites.toVector.sequenceU match {
          case -\/(e) => Future.successful(e)
          case \/-(updates) =>
            Future.sequence(newUserIds map {
              case (userId, keyHashes) =>
                Future.sequence(immutable.Seq(
                  persist.GroupUser.addUser(group.id, userId, keyHashes),
                  persist.UserGroups.addGroup(userId, group.id)
                ))
            }) flatMap { _ =>
              Future.sequence(immutable.Seq(
                persist.GroupUser.addUser(group.id, currentUser.uid, broadcast.ownKeys map (_.keyHash) toSet),
                persist.UserGroups.addGroup(currentUser.uid, group.id)
              ))
            } flatMap { _ =>
              Future.sequence(broadcast.ownKeys map { key =>
                authIdFor(currentUser.uid, key.keyHash) flatMap {
                  case Some(\/-(authId)) =>
                    for {
                      users <- Future.sequence(
                        ((newUserIds map (_._1)) :+ currentUser.uid) map { newUserId =>
                          getUserIdStruct(newUserId, authId)
                        }
                      )
                    } yield {
                      NewUpdatePush(
                        authId,
                        updateProto.GroupCreated(
                          groupId = group.id,
                          accessHash = group.accessHash,
                          title = group.title,
                          invite = EncryptedRSAPackage (
                            keyHash = key.keyHash,
                            aesEncryptedKey = key.aesEncryptedKey,
                            message = broadcast.encryptedMessage
                          ),
                          users = users.flatten
                        )
                      ).right
                    }
                  case _ =>
                    Future.successful(Error(404, "OWN_KEY_HASH_NOT_FOUND", s"", false).left)
                }
              }) map (_.toVector.sequenceU) flatMap {
                case -\/(e) =>
                  Future.successful(e)
                case \/-(ownUpdates) =>
                  (updates ++ ownUpdates) map (updatesBrokerRegion ! _)

                  for {
                    s <- getState(currentUser.authId)
                  } yield {
                    Ok(ResponseCreateGroup(group.id, group.accessHash, s._1, s._2))
                  }
              }
            }
        }
      }
    }
  }

  protected def handleRequestEditGroupAvatar(
    groupId: Int, accessHash: Long, fileLocation: models.FileLocation
  ): Future[RpcResponse] = {
    withGroup(groupId, accessHash) { group =>
      val sizeLimit: Long = 1024 * 1024 // TODO: configurable

      fileRecord.getFileLength(fileLocation.fileId.toInt) flatMap { len =>
        if (len > sizeLimit)
          Future successful Error(400, "FILE_TOO_BIG", "", false)
        else
          AvatarUtils.scaleAvatar(fileRecord, fileLocation) flatMap { a =>
            persist.Group.updateAvatar(groupId, a) map { _ =>
              withGroupUserAuthIds(groupId) { authIds =>
                authIds foreach { authId =>
                  updatesBrokerRegion ! UpdatesBroker.NewUpdatePush(
                    authId,
                    updateProto.GroupAvatarChanged(groupId, Some(a))
                  )
                }
              }
              Ok(ResponseAvatarChanged(a))
            }
          } recover {
            case e =>
              log.warning(s"Failed while updating avatar: $e")
              Error(400, "IMAGE_LOAD_ERROR", "", false)
          }
      }
    }
  }

  protected def handleRequestEditGroupTitle(
    groupId: Int,
    accessHash: Long,
    title: String
  ): Future[RpcResponse] = {
    withGroup(groupId, accessHash) { group =>
      for {
        _ <- persist.Group.updateTitle(groupId, title)
        s <- getState(currentUser.authId)
      } yield {
        persist.GroupUser.getUsers(groupId) onSuccess {
          case groupUserIds =>
            groupUserIds foreach { groupUserId =>
              for {
                authIds <- getAuthIds(groupUserId)
              } yield {
                authIds map { authId =>
                  updatesBrokerRegion ! NewUpdatePush(
                    authId,
                    updateProto.GroupTitleChanged(
                      groupId, title
                    )
                  )
                }
              }
            }
        }

        Ok(ResponseSeq(s._1, s._2))
      }
    }
  }

  protected def handleRequestRemoveUser(
    groupId: Int,
    accessHash: Long,
    userId: Int,
    userAccessHash: Long
  ): Future[RpcResponse] = {
    persist.Group.getEntity(groupId) flatMap { optGroup =>
      optGroup map { group =>
        if (group.creatorUserId != currentUser.uid) {
          Future.successful(Error(403, "NO_PERMISSION", "You are not creator of this group.", false))
        } else if (group.accessHash != accessHash) {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid group access hash.", false))
        } else {
          getUsers(userId) flatMap {
            case users if users.isEmpty =>
              Future.successful(Error(404, "USER_DOES_NOT_EXISTS", "User does not exists.", true))
            case users =>
              val (_, checkUser) = users.head

              if (ACL.userAccessHash(currentUser.authId, checkUser) != userAccessHash) {
                Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid user access hash.", false))
              } else {
                persist.GroupUser.getUsers(groupId) flatMap { groupUserIds =>
                  if (groupUserIds.contains(userId)) {
                    for {
                      _ <- Future.sequence(immutable.Seq(
                        persist.GroupUser.removeUser(groupId, userId),
                        persist.UserGroups.removeGroup(userId, groupId)
                      ))
                      s <- getState(currentUser.authId)
                    } yield {
                      groupUserIds foreach { groupUserId =>
                        for {
                          authIds <- getAuthIds(groupUserId)
                        } yield {
                          authIds map { authId =>
                            updatesBrokerRegion ! NewUpdatePush(
                              authId,
                              updateProto.GroupUserKick(
                                groupId, userId, currentUser.uid
                              )
                            )
                          }
                        }
                      }

                      Ok(ResponseSeq(s._1, s._2))
                    }
                  } else {
                    Future.successful(Error(404, "USER_DOES_NOT_EXISTS", "There is no participant with such userId in this group.", false))
                  }
                }
              }
          }
        }
      } getOrElse Future.successful(Error(404, "GROUP_DOES_NOT_EXISTS", "Group does not exists.", true))
    }
  }

  protected def handleRequestLeaveGroup(
    groupId: Int,
    accessHash: Long
  ): Future[RpcResponse] = {
    persist.Group.getEntity(groupId) flatMap { optGroup =>
      optGroup map { group =>
        if (group.accessHash != accessHash) {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false))
        } else {
          for {
            _ <- Future.sequence(immutable.Seq(
              persist.GroupUser.removeUser(groupId, currentUser.uid),
              persist.UserGroups.removeGroup(currentUser.uid, groupId)
            ))
            groupUserIds <- persist.GroupUser.getUsers(groupId)
            s <- getState(currentUser.authId)
          } yield {
            (groupUserIds :+ currentUser.uid) foreach { userId =>
              for {
                authIds <- getAuthIds(userId)
              } yield {
                authIds foreach {
                  case currentUser.authId =>
                  case authId =>
                    updatesBrokerRegion ! NewUpdatePush(authId, updateProto.GroupUserLeave(
                      groupId, currentUser.uid
                    ))
                }
              }
            }
            Ok(ResponseSeq(s._1, s._2))
          }
        }
      } getOrElse Future.successful(Error(404, "GROUP_DOES_NOT_EXISTS", "Group does not exists.", true))
    }
  }

  protected def handleRequestMessageReceived(userId: Int, randomId: Long, accessHash: Long): Future[RpcResponse] = {
    withUsers(uid, accessHash, currentUser) { users =>
      users map {
        case (_, u) =>
          updatesBrokerRegion ! NewUpdatePush(u.authId, updateProto.MessageReceived(currentUser.uid, randomId))
      }

      Future.successful(Ok(ResponseVoid()))
    }
  }

  // TODO: DRY
  protected def handleRequestMessageRead(userId: Int, randomId: Long, accessHash: Long): Future[RpcResponse] = {
    withUsers(uid, accessHash, currentUser) { users =>
      users map {
        case (_, u) =>
          updatesBrokerRegion ! NewUpdatePush(u.authId, updateProto.MessageRead(currentUser.uid, randomId, System.currentTimeMillis()))
      }

      Future.successful(Ok(ResponseVoid()))
    }
  }

  protected def handleRequestSendGroupMessage(groupId: Int,
    accessHash: Long,
    randomId: Long,
    message: EncryptedAESMessage
  ): Future[RpcResponse] = {

    val fgroupUserIds = persist.GroupUser.getUsers(groupId)
    persist.Group.getEntity(groupId) flatMap { optGroup =>
      optGroup map { group =>
        if (group.accessHash != accessHash) {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false))
        } else if (group.keyHash != message.keyHash) {
          Future.successful(Error(400, "WRONG_KEY", "Invalid group key hash.", false))
        } else {
          fgroupUserIds flatMap { userIds =>
            if (userIds.contains(currentUser.uid)) {
              val updatesFutures = userIds map { userId =>
                getUsers(userId) map {
                  case users =>
                    users.toSeq map {
                      case (_, user) =>
                        (
                          user.authId,
                          updateProto.GroupMessage(
                            senderUID = currentUser.uid,
                            groupId = group.id,
                            EncryptedAESPackage (
                              keyHash = message.keyHash,
                              message = message.encryptedMessage
                            )
                          )
                        )
                    }
                }
              }

              Future.sequence(updatesFutures) map { updates =>
                updates.toVector.flatten foreach {
                  case (authId, update) =>
                    updatesBrokerRegion ! NewUpdatePush(authId, update)
                }
              }

              for {
                s <- getState(currentUser.authId)
              } yield {
                Ok(ResponseSeq(s._1, s._2))
              }
            } else {
              Future.successful(Error(403, "NO_PERMISSION", "You are not a member of this group.", true))
            }
          }
        }
      } getOrElse Future.successful(Error(404, "GROUP_DOES_NOT_EXISTS", "Group does not exists.", true))
    }
  }

  private def getSeq(authId: Long): Future[Int] = {
    ask(updatesBrokerRegion, UpdatesBroker.GetSeq(authId)).mapTo[Int]
  }

  protected def getState(authId: Long)(implicit session: CSession): Future[(Int, Option[UUID])] = {
    for {
      seq <- getSeq(authId)
      muuid <- persist.SeqUpdate.getState(authId)
    } yield (seq, muuid)
  }

  protected def withGroup(groupId: Int, accessHash: Long)(f: models.Group => Future[RpcResponse]): Future[RpcResponse] = {
    persist.Group.getEntity(groupId) flatMap { optGroup =>
      optGroup map { group =>
        if (group.accessHash != accessHash) {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", s"Invalid group access hash.", false))
        } else {
          f(group)
        }
      } getOrElse Future.successful(Error(404, "GROUP_DOES_NOT_EXISTS", "Group does not exists.", true))
    }
  }

  protected def withUsers(
    destUserId: Int,
    accessHash: Long,
    currentUser: models.User
  )(f: Seq[(Long, models.User)] => Future[RpcResponse])(
    implicit session: CSession
  ): Future[RpcResponse] = {
    getUsers(destUserId) flatMap {
      case users if users.isEmpty =>
        Future.successful(Error(404, "USER_DOES_NOT_EXISTS", "User does not exists.", true))
      case users =>
        val (_, user) = users.head

        if (ACL.userAccessHash(currentUser.authId, user) == accessHash) {
          f(users)
        } else {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false))
        }
    }
  }

  protected def withUser(
    destUserId: Int,
    accessHash: Long,
    currentUser: models.User
  )(f: models.User => Future[RpcResponse])(
    implicit session: CSession
  ): Future[RpcResponse] = {
    persist.User.getEntity(destUserId) flatMap {
      case Some(destUserEntity) =>
        if (ACL.userAccessHash(currentUser.authId, destUserEntity) != accessHash) {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false))
        } else {
          f(destUserEntity)
        }
      case None =>
        Future.successful(Error(400, "INTERNAL_ERROR", "Destination user not found", true))
    }
   }*/
}
