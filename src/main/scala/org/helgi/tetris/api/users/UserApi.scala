package org.helgi.tetris.api.users

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
import org.helgi.tetris.api.*
import org.helgi.tetris.api.users.UserApiMessage.Signup
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.model.User
import org.helgi.tetris.repository.UserRepository
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}


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

