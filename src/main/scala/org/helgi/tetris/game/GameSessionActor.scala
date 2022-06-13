package org.helgi.tetris.game

import akka.actor.SupervisorStrategy.*
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy}
import org.helgi.tetris.api.game.GameProtocol
import org.helgi.tetris.game.GameSessionMessage.*
import org.helgi.tetris.model.GameResult
import org.helgi.tetris.repository.{GameRepoCommand, GameResultRepository}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}

enum GameSessionMessage:
  case Start(broker: ActorRef)
  case Update(gd: GameData)
  case GameOver(gd: GameData)

case class GameOptions(initLvl: Int = 0, grid: (Int, Int) = (10, 17))

case class GameSession(ticker: Cancellable, gameData: GameData, startedAt: Instant)

class GameSessionActor(userId: Option[Long], gameOptions: GameOptions,
                       grRepo: ActorRef) extends Actor :

  private val initGameData = GameData(0, 0, GameState.init(gameOptions.grid))

  private val gameActor = context.actorOf(GameActor.props(initGameData))

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(3) {
    case _ => Resume
  }

  override def receive: Receive = _ match
    case Start(broker) =>
      val initSpeed = getSpeed(gameOptions.initLvl)
      val scheduledTick = startTicker(initSpeed)
      context.become(activeGame(broker, GameSession(scheduledTick, initGameData, Instant.now())))

  private def activeGame(broker: ActorRef, gameSession: GameSession): Receive = _ match
    case c: GameCommand =>
      gameActor ! c
    case Update(updatedGD) =>
      if gameSession.gameData.lvl < updatedGD.lvl then
        gameSession.ticker.cancel()
        val updatedSpeed = getSpeed(gameSession.gameData.lvl)
        val updatedTicker = startTicker(updatedSpeed)
        context.become(activeGame(broker, gameSession.copy(ticker = updatedTicker, gameData = updatedGD)))
      else context.become(activeGame(broker, gameSession.copy(gameData = updatedGD)))
      broker ! GameProtocol.GameStateMsg(updatedGD)
    case GameOver(lastGd: GameData) =>
      gameSession.ticker.cancel()
      broker ! GameProtocol.GameStateMsg(lastGd)
      broker ! GameProtocol.GameOver
      userId.foreach { id =>
        grRepo ! GameRepoCommand.Save(
          GameResult(0, id, lastGd.score, lastGd.gs.totalLinesCleared, lastGd.lvl, gameSession.startedAt, Instant.now())
        )
      }
      context.stop(self)

  private def getSpeed(lvl: Int): FiniteDuration =
    FiniteDuration((math.pow(1 - 0.25, lvl) * 1000).round, TimeUnit.MILLISECONDS)

  private def startTicker(speed: FiniteDuration): Cancellable =
    context.system.scheduler.scheduleAtFixedRate(0.milli, speed) { () =>
      gameActor ! GameCommand.Tick
    }

object GameSessionActor:
  def props(userId: Option[Long], gameOptions: GameOptions,
            grRepo: ActorRef): Props = Props(new GameSessionActor(userId, gameOptions, grRepo))
