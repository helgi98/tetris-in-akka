package org.helgi.tetris.repository

import akka.actor.{Actor, ActorLogging, Props}
import org.helgi.tetris.repository.{GameRepoActor, GameRepoCommand}
import org.helgi.tetris.model.GameResult
import org.helgi.tetris.repository.{GameRepoActor, GameRepoCommand, GameResultRepository}

enum GameRepoCommand:
  case Save(gameResult: GameResult)

class GameRepoActor(resultRepository: GameResultRepository) extends Actor with ActorLogging :

  import GameRepoCommand.*

  override def receive: Receive = _ match
    case Save(gameResult) =>
      try
        resultRepository.saveGameResult(gameResult)
      catch
        // We don't want to fail this actor in case of exception
        case ex: Exception => log.error(ex, "Failed to save game result")

object GameRepoActor:
  def props(resultRepository: GameResultRepository): Props = Props(new GameRepoActor(resultRepository))