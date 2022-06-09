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
import org.helgi.tetris.api.users.{UserApi, UserApiActor}
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.repository.UserRepository

import scala.language.implicitConversions
import org.helgi.tetris.util.Conversions.javaToScalaDuration

import scala.concurrent.ExecutionContextExecutor

class RestApi(system: ActorSystem, val ds: HikariDataSource, conf: Config) extends UserApi with GameApi :

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  implicit val requestTimeout: Timeout = Timeout(conf.getDuration("akka.http.server.request-timeout"))

  private val ta: HikariTransactor[IO] = {
    HikariTransactor(ds, executionContext)
  }

  override val userActor: ActorRef = {
    val userRepo = UserRepository(ta)
    val jwtConfig = JwtConfig(conf.getString("jwt.secret"),
      conf.getDuration("jwt.expiration"))
    system.actorOf(UserApiActor.props(userRepo, jwtConfig))
  }

  def routes: Route = pathPrefix("api") {
    userRoutes ~ gameRoutes
  }


trait GameApi:
  def gameRoutes: Route = ???

