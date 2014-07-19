package com.secretapp.backend.services

package object transport {
  sealed trait HandleError
  case class ParseError(msg: String) extends HandleError // error which caused when package parsing (we can't parse authId/sessionId/messageId)
}
