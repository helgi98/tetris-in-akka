package org.helgi.tetris.util

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.{BasicDirectives, HeaderDirectives, RouteDirectives}
import io.jsonwebtoken.Jwts

object JwtDirectives:

  import BasicDirectives.*
  import HeaderDirectives.*
  import RouteDirectives.*

  def authenticateOpt(secret: String): Directive1[Option[Long]] = {
    optionalHeaderValueByName("Authorization")
      .map(_.map(parseToken(secret, _)))
  }

  def authenticate(secret: String): Directive1[Long] = {
    headerValueByName("Authorization")
      .map(parseToken(secret, _))
      .map(Option(_))
      .flatMap {
        case Some(userId) =>
          provide(userId)
        case None =>
          reject
      }
  }

  private def parseToken(secret: String, token: String): Long =
    Jwts.parser()
      .setSigningKey(secret)
      .parseClaimsJwt(token.split(" ").tail.head)
      .getBody
      .getSubject.toInt
