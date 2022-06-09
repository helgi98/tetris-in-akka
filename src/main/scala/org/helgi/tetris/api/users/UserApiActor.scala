package org.helgi.tetris.api.users

import akka.actor.{Actor, Props}
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.model.User
import org.helgi.tetris.repository.UserRepository

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

enum UserApiMessage:
  case Signup(registrationData: RegistrationData)
  case Login(loginPassword: LoginPassword)

class UserApiActor(repo: UserRepository, jwtConfig: JwtConfig)(implicit val ec: ExecutionContext) extends Actor :
  import UserApiMessage.*

  override def receive: Receive = _ match
    case Signup(rd) =>

      repo.checkUserExist(rd.username, rd.email).flatMap { exists =>
        if !exists then
          repo.createUser(User(0, rd.username, rd.displayName, rd.email, sha256Hex(rd.password)))
            .map(Some(_))
        else
          Future.successful(None)
      }.foreach {
        sender() ! _.map(u => UserData(u.username, u.displayName, u.email))
      }
    case Login(lp: LoginPassword) =>
      val user = if lp.login contains "@" then
        repo.findUserByEmail(lp.login)
      else
        repo.findUserByUsername(lp.login)

      user.foreach {
        sender() ! _.flatMap { u =>
          if sha256Hex(lp.password).equals(u.password) then
            Some(LoginToken(u.username, encodeToken(u.username)))
          else None
        }
      }

  private def encodeToken(username: String): String =
    Jwts.builder()
      .setSubject(username)
      .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.expirationTime.toMillis))
      .signWith(SignatureAlgorithm.HS512, jwtConfig.secret)
      .compact()

object UserApiActor:
  def props(repo: UserRepository, jwtConfig: JwtConfig)(implicit ec: ExecutionContext): Props =
    Props(new UserApiActor(repo, jwtConfig))