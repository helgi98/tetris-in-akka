package org.helgi.tetris.game

import akka.actor.SupervisorStrategy.*
import akka.actor.{Actor, Cancellable, OneForOneStrategy, SupervisorStrategy}
import org.helgi.tetris.game.GameSessionMsg.*
import org.helgi.tetris.model.GameResult
import org.helgi.tetris.repository.GameResultRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}

enum GameSessionMsg:
  case Start
  case GameOver(gd: GameData)

case class GameOptions(initLvl: Int, grid: (Int, Int) = (10, 20))

case class GameSession(ticker: Cancellable, gameData: GameData, startedAt: Instant)

class GameSessionActor(userId: Option[Long], gameOptions: GameOptions,
                       grRepo: GameResultRepository) extends Actor :

  private val initGameData = GameData(0, 0, GameState.init(gameOptions.grid))
  private val gameActor = context.actorOf(GameActor.props(initGameData))

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(3) {
    case _ => Resume
  }

  def getSpeed(lvl: Int): FiniteDuration = FiniteDuration((math.pow(1 - 0.25, lvl) * 1000).round, TimeUnit.MILLISECONDS)

  override def receive: Receive = _ match
    case Start =>
      val initSpeed = getSpeed(gameOptions.initLvl)
      val scheduledTick = context.system.scheduler.scheduleAtFixedRate(Duration.Zero, initSpeed) { () =>
        gameActor ! GameCommand.Tick
      }
      context.become(activeGame(GameSession(scheduledTick, initGameData, Instant.now())))

  def startTicker(speed: FiniteDuration): Cancellable =
    context.system.scheduler.scheduleAtFixedRate(Duration.Zero, speed) { () =>
      gameActor ! GameCommand.Tick
    }

  def activeGame(gameSession: GameSession): Receive = _ match
    case GameOver(gd: GameData) =>
      gameSession.ticker.cancel()
      // TODO send final result
      userId.foreach { id =>
        grRepo.saveGameResult(GameResult(0, id, gd.score, gd.gs.totalLinesCleared, gd.lvl,
          gameSession.startedAt, Instant.now()))
      }
    case updatedGD: GameData =>
      if gameSession.gameData.lvl < updatedGD.lvl then
        gameSession.ticker.cancel()
        val updatedSpeed = getSpeed(gameSession.gameData.lvl)
        val updatedTicker = startTicker(updatedSpeed)
        context.become(activeGame(gameSession.copy(ticker = updatedTicker, gameData = updatedGD)))
      else context.become(activeGame(gameSession.copy(gameData = updatedGD)))
    // TODO resend state
    case c: GameCommand =>
      gameActor ! c



