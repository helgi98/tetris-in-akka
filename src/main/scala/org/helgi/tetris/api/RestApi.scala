package org.helgi.tetris.api

import akka.actor.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.*
import akka.http.scaladsl.server.Directives.*
import akka.util.Timeout
import cats.effect.IO
import cats.effect.IO.asyncForIO
import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.hikari.HikariTransactor
import org.helgi.tetris.api.game.GameApi
import org.helgi.tetris.api.users.{UserApi, UserApiActor}
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.repository.{GameResultRepository, UserRepository}
import org.helgi.tetris.util.Conversions.javaToScalaDuration

import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions

class RestApi(val system: ActorSystem, ds: HikariDataSource, conf: Config) extends UserApi with GameApi
  with CorsHandler :

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  implicit val requestTimeout: Timeout = Timeout(conf.getDuration("akka.http.server.request-timeout"))
  // UserApi dependencies
  override val jwtConfig: JwtConfig = JwtConfig(conf.getString("jwt.secret"),
    conf.getDuration("jwt.expiration"))
  override val userRepo: UserRepository = UserRepository(ta)
  override val gameRepo: GameResultRepository = GameResultRepository(ta)
  private val ta: HikariTransactor[IO] = {
    HikariTransactor(ds, executionContext)
  }

  def routes: Route = pathPrefix("api") {
    corsHandler {
      userRoutes ~ gameRoutes
    }
  }

