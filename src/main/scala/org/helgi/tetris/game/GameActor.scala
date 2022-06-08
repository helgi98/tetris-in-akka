package org.helgi.tetris.game

import akka.actor.{Actor, Props}
import org.helgi.tetris.game.GameSessionMsg.GameOver

import scala.annotation.tailrec

enum GameCommand:
  case Rotate, Left, Right, Tick, Drop, Hold

case class GameData(score: Int, lvl: Int, gs: GameState):
  def withState(state: GameState): GameData = copy(gs = state)

class GameActor(initState: GameData) extends Actor :

  override def receive: Receive = onMessage(initState)

  def onMessage(data: GameData): Receive = _ match
    // Game Commands
    case GameCommand.Rotate =>
      transitState(data)(_.rotate)
    case GameCommand.Left =>
      transitState(data)(_.left)
    case GameCommand.Right =>
      transitState(data)(_.right)
    case GameCommand.Tick =>
      transitState(data)(_.tick)
    case GameCommand.Drop =>
      transitState(data)(_.drop)
    case GameCommand.Hold =>
      transitState(data)(_.hold)

  def calcScore(linesCleared: Int, lvl: Int): Int =
    @tailrec
    def fact(n: Int, acc: Int): Int = if n <= 1 then acc else fact(n - 1, acc * n)

    fact(linesCleared, 1) * (50 * (lvl + 1))

  def checkLinesCleared(old: GameData, updatedState: GameState): GameData =
    val lines = updatedState.totalLinesCleared - old.gs.totalLinesCleared
    if lines > 0 then
      val score = calcScore(lines, old.lvl)
      val lvl = updatedState.totalLinesCleared / 10
      GameData(score, lvl, updatedState)
    else old.withState(updatedState)

  def transitState(data: GameData)(f: GameState => GameState): Unit =
    val updatedState = f(data.gs)
    val updatedData = checkLinesCleared(data, updatedState)

    if updatedState.status == GameStatus.Over then
      context.become {
        _ =>
      }
      sender() ! GameOver(updatedData)
    else
      context.become(onMessage(updatedData))
      sender() ! updatedData


object GameActor:
  def props(gameData: GameData): Props = Props(new GameActor(gameData))
