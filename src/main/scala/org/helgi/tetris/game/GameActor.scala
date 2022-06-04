package org.helgi.tetris.game

import akka.actor.Actor

enum GameCommand:
  case Rotate, Left, Right, Tick, Drop, Hold

class GameActor(initState: GameState) extends Actor :

  import GameCommand.*

  override def receive: Receive = onMessage(initState)

  def onMessage(state: GameState): Receive =
    case Rotate => context.become(onMessage(state.rotate))
    case Left => context.become(onMessage(state.left))
    case Right => context.become(onMessage(state.right))
    case Tick => context.become(onMessage(state.tick))
    case Hold => context.become(onMessage(state.hold))
    case Drop => context.become(onMessage(state.drop))


