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
import org.helgi.tetris.api.*
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.repository.UserRepository
import org.helgi.tetris.util.JwtDirectives.*
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}


trait UserApi extends UserJsonSupport :

  import UserApiMessage.*

  val system: ActorSystem

  implicit val executionContext: ExecutionContext

  implicit val requestTimeout: Timeout

  val userRepo: UserRepository

  val jwtConfig: JwtConfig

  private val userActor = system.actorOf(UserApiActor.props(userRepo, jwtConfig))

  def userRoutes: Route =
    pathPrefix("user") {
      path("signup") {
        pathEndOrSingleSlash {
          post {
            entity(as[RegistrationData]) { rd =>
              onSuccess(userActor.ask(Signup(rd)).mapTo[Option[UserData]]) {
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
                  case Some(lt) => complete(OK, lt)
                  case None => complete(NotFound)
                }
              }
            }
          }
        } ~
        path("info") {
          pathEndOrSingleSlash {
            get {
              authenticate(jwtConfig.secret) { userId =>
                complete(OK, userActor.ask(Info(userId)).mapTo[Option[UserData]].map(_.get))
              }
            }
          }
        }
    }

