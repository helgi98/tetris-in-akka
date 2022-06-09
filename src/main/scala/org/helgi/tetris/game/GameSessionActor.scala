package org.helgi.tetris.game

import akka.actor.SupervisorStrategy.*
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, PoisonPill, SupervisorStrategy}
import org.helgi.tetris.game.GameSessionMessage.*
import org.helgi.tetris.model.GameResult
import org.helgi.tetris.repository.{GameResultRepoActor, GameResultRepoCommand, GameResultRepository}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}

enum GameSessionMessage:
  case Start
  case GameOver(gd: GameData)

case class GameOptions(initLvl: Int, grid: (Int, Int) = (10, 20))

case class GameSession(ticker: Cancellable, gameData: GameData, startedAt: Instant)

class GameSessionActor(userId: Option[Long], gameOptions: GameOptions,
                       grRepo: ActorRef) extends Actor :

  private val initGameData = GameData(0, 0, GameState.init(gameOptions.grid))

  private val gameActor = context.actorOf(GameActor.props(initGameData))

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(3) {
    case _ => Resume
  }

  def getSpeed(lvl: Int): FiniteDuration = FiniteDuration((math.pow(1 - 0.25, lvl) * 1000).round, TimeUnit.MILLISECONDS)

  override def receive: Receive = _ match
    case Start =>
      val initSpeed = getSpeed(gameOptions.initLvl)
      val scheduledTick = context.system.scheduler.scheduleAtFixedRate(0.milli, initSpeed) { () =>
        gameActor ! GameCommand.Tick
      }
      context.become(activeGame(GameSession(scheduledTick, initGameData, Instant.now())))

  def startTicker(speed: FiniteDuration): Cancellable =
    context.system.scheduler.scheduleAtFixedRate(0.milli, speed) { () =>
      gameActor ! GameCommand.Tick
    }

  def activeGame(gameSession: GameSession): Receive = _ match
    case c: GameCommand =>
      gameActor ! c
    case updatedGD: GameData =>
      if gameSession.gameData.lvl < updatedGD.lvl then
        gameSession.ticker.cancel()
        val updatedSpeed = getSpeed(gameSession.gameData.lvl)
        val updatedTicker = startTicker(updatedSpeed)
        context.become(activeGame(gameSession.copy(ticker = updatedTicker, gameData = updatedGD)))
      else context.become(activeGame(gameSession.copy(gameData = updatedGD)))
    // TODO resend state
    case GameOver(gd: GameData) =>
      gameSession.ticker.cancel()
      // TODO send final result
      userId.foreach { id =>
        grRepo ! GameResultRepoCommand.Save(
          GameResult(0, id, gd.score, gd.gs.totalLinesCleared, gd.lvl, gameSession.startedAt, Instant.now())
        )
      }
      context.stop(self)



