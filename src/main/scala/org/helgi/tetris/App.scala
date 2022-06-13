package org.helgi.tetris

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import org.helgi.tetris.api.RestApi

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}


object App:
  val logger = Logger[App]

  def main(args: Array[String]): Unit =
    implicit val system: ActorSystem = ActorSystem("akka-tetris")
    implicit val executor: ExecutionContext = system.dispatcher

    try
      val config = ConfigFactory.load()

      val ds = buildDataSource(config)
      Flyway.configure().dataSource(ds).load().migrate()

      val restApi = RestApi(system, ds, config).routes

      val host = config.getString("http.host")
      val port = config.getInt("http.port")
      Http().newServerAt(host, port).bindFlow(restApi)

      Await.result(system.whenTerminated, Duration.Inf)
    catch
      case ex =>
        logger.error("Error occurred during start up. Exiting", ex)
        system.terminate()

  def buildDataSource(conf: Config): HikariDataSource =
    val hikariConfig: HikariConfig = HikariConfig()
    hikariConfig.setDriverClassName(conf.getString("db.driver"))
    hikariConfig.setJdbcUrl(conf.getString("db.url"))
    hikariConfig.setUsername(conf.getString("db.user"))
    hikariConfig.setPassword(conf.getString("db.password"))

    HikariDataSource(hikariConfig)

