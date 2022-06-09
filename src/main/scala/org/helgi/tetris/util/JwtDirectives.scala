package org.helgi.tetris.util

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.{BasicDirectives, HeaderDirectives, RouteDirectives}
import io.jsonwebtoken.Jwts

object JwtDirectives:

  import BasicDirectives.*
  import HeaderDirectives.*
  import RouteDirectives.*

  def authenticateOpt(secret: String): Directive1[Option[String]] = {
    headerValueByName("Authorization")
      .map(parseToken(secret, _))
      .map(Option(_))
  }

  def authenticate(secret: String): Directive1[String] = {
    headerValueByName("Authorization")
      .map(parseToken(secret, _))
      .map(Option(_))
      .flatMap {
        case Some(username) =>
          provide(username)
        case None =>
          reject
      }
  }

  private def parseToken(secret: String, token: String): String =
    Jwts.parser()
      .setSigningKey(secret)
      .parseClaimsJwt(token.split(" ").tail.head)
      .getBody
      .getSubject
