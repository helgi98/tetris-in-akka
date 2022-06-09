package org.helgi.tetris.repository

import akka.actor.{Actor, ActorLogging}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import doobie.util.fragments.*
import doobie.util.transactor.Transactor
import org.helgi.tetris.model.GameResult

import java.time.Instant
import scala.concurrent.Future

trait GameResultRepository:
  def getGameResultsByUser(userId: Long): Future[List[GameResult]]

  def saveGameResult(gr: GameResult): Future[GameResult]

object GameResultRepository:

  def apply(ta: Transactor[IO]): GameResultRepository =
    new GameResultRepository :
      override def getGameResultsByUser(userId: Long): Future[List[GameResult]] =
        (gameResultSelect ++ whereAnd(byUserId(userId)))
          .query[GameResult].to[List].transact(ta).unsafeToFuture()

      override def saveGameResult(gr: GameResult): Future[GameResult] =
        (for
          _ <- gameResultInsert(gr).run
          id <- sql"select lastval()".query[Long].unique
          g <- gameResultSelectById(id).unique
        yield g).transact(ta).unsafeToFuture()

  def gameResultSelectById(id: Long): doobie.Query0[GameResult] =
    (gameResultSelect ++ whereAnd(byId(id))).query[GameResult]

  def gameResultSelect: doobie.Fragment = fr"SELECT * FROM game_result gr"

  def byId(id: Long): doobie.Fragment = fr"gr.id = $id"

  def byUserId(userId: Long): doobie.Fragment = fr"gr.user_id = $userId"

  def gameResultInsert(gr: GameResult): doobie.Update0 =
    sql"""
         |INSERT INTO game_result(user_id, score, lines_cleared, lvl, started_at, finished_at)
         |VALUE (${gr.userId}, ${gr.score}, ${gr.linesCleared}, ${gr.lvl}, ${gr.startedAt}, ${gr.finishedAt})
         |""".stripMargin.update

enum GameResultRepoCommand:
  case Save(gameResult: GameResult)

class GameResultRepoActor(resultRepository: GameResultRepository) extends Actor with ActorLogging :

  import GameResultRepoCommand.*

  override def receive: Receive = _ match
    case Save(gameResult) =>
      try
        resultRepository.saveGameResult(gameResult)
      catch
        // We don't want to fail this actor in case of exception
        case ex: Exception => log.error(ex, "Failed to save game result")
  