package org.helgi.tetris.api

import akka.actor.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.server.*
import akka.http.scaladsl.server.Directives.*
import akka.pattern.ask
import akka.util.Timeout
import doobie.hikari.HikariTransactor
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.helgi.tetris.api.UserApiMessage.Signup
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.model.User
import org.helgi.tetris.repository.UserRepository
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

case class RegistrationData(username: String, displayName: String, email: String, password: String)

case class UserData(username: String, displayName: String, email: String)

case class LoginPassword(login: String, password: String)

case class LoginToken(login: String, token: String)

trait UserJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val registrationDataFormat: RootJsonFormat[RegistrationData] = jsonFormat4(RegistrationData.apply)
  implicit val userDataFormat: RootJsonFormat[UserData] = jsonFormat3(UserData.apply)
  implicit val loginPasswordFormat: RootJsonFormat[LoginPassword] = jsonFormat2(LoginPassword.apply)
  implicit val loginTokenFormat: RootJsonFormat[LoginToken] = jsonFormat2(LoginToken.apply)
}

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


trait UserApi extends UserJsonSupport :

  import UserApiMessage.*

  implicit val executionContext: ExecutionContext

  implicit val requestTimeout: Timeout

  val userActor: ActorRef

  def userRoutes: Route =
    pathPrefix("user") {
      path("signup") {
        pathEndOrSingleSlash {
          post {
            entity(as[RegistrationData]) { rd =>
              onSuccess(userActor.ask(Signup(rd)).mapTo[Option[UserData]]) {
                _ match
                  case Some(ud) => complete(Created, ud)
                  case None => complete(Conflict)
              }
            }
          }
        }
      } ~
        path("login") {
          pathEndOrSingleSlash {
            post {
              entity(as[LoginPassword]) { idPass =>
                onSuccess(userActor.ask(Login(idPass)).mapTo[Option[LoginToken]]) {
                  _ match
                    case Some(lt) => complete(OK, lt)
                    case None => complete(NotFound)
                }
              }
            }
          }
        }
    }

