package org.helgi.tetris.game

import akka.actor.Actor
import org.helgi.tetris.game.Pos.*

object Pos:
  type Pos = (Int, Int)

  extension (p: Pos)
    def x = p._1
    def y = p._2
    def +(o: Pos) = (p.x + o.x, p.y + o.y)

enum PieceKind:
  case I
  case J
  case L
  case O
  case S
  case T
  case Z

case class Piece(pos: Pos, kind: PieceKind, theta: Int = 0):
  require(Set(0, 90, 180, 270).contains(theta))

  def rotate: Piece = rotate(1)

  def rotate(times: Int): Piece = copy(theta = (theta + 90 * times) % 360)

  def right: Piece = right(1)

  def right(times: Int): Piece = moveHorizontally(times)

  def left: Piece = left(1)

  def left(times: Int): Piece = moveHorizontally(-times)

  def moveHorizontally(dx: Int): Piece = copy(pos = (pos.x + dx, pos.y))

  def down: Piece = down(1)

  def down(times: Int): Piece = moveVertically(-times)

  def moveVertically(dy: Int): Piece = copy(pos = (pos.x, pos.y + dy))

  def blocks: List[Pos] = (kind match {
    case PieceKind.I => List((-1, 0), (0, 0), (1, 0), (2, 0))
    case PieceKind.J => List((-1, 1), (-1, 0), (0, 0), (1, 0))
    case PieceKind.L => List((-1, 0), (0, 0), (1, 0), (1, 1))
    case PieceKind.O => List((0, 1), (0, 0), (1, 0), (1, 1))
    case PieceKind.S => List((-1, 0), (0, 0), (0, 1), (1, 1))
    case PieceKind.T => List((-1, 0), (0, 0), (0, 1), (1, 0))
    case PieceKind.Z => List((-1, 1), (0, 1), (0, 0), (1, 0))
  }).map(rotateBlock).map(_ + pos)

  def rotateBlock(block: Pos): Pos =
    val c = math.cos(-theta.toDouble.toRadians).round.toInt
    val s = math.sin(-theta.toDouble.toRadians).round.toInt

    (block.x * c - block.y * s, block.x * s + block.y * c)


object Test:

  def main(args: Array[String]): Unit =
    val p = Piece((3, 4), PieceKind.I)

    println(p.rotate(4) == p)

    println(mkTetrisField(p.blocks))
    println(mkTetrisField(p.rotate.blocks))
    println(mkTetrisField(p.rotate(2).blocks))
    println(mkTetrisField(p.rotate(3).blocks))
    println(mkTetrisField(p.rotate(4).blocks))

  def mkTetrisField(ps: Seq[Pos]): String =
    def mkNthRow(i: Int): String =
      (for j <- 1 to 10
        yield if ps.contains((j, i)) then "O" else "-").mkString

    print("\n")

    (for i <- 20 to 1 by -1
      yield mkNthRow(i)).mkString("\n")


enum GameCommand:
  case Rotate
  case Left
  case Right
  case Tick
  case Drop
  case Hold

case class GameState(gridSize: (Int, Int), blocks: List[Pos],
                     currentPiece: Piece, nextPiece: Piece,
                     heldPiece: Option[Piece] = None, canHold: Boolean = true):

  val initPos: Pos = (gridSize._1 / 2, gridSize._2 / 2)

  def rotate: GameState = transit(_.rotate)

  def left: GameState = transit(_.left)

  def right: GameState = transit(_.right)

  def tick: GameState = transit(_.down, {
    this
  })

  def drop: GameState = ???

  def hold: GameState = ???

  def inBounds(pos: Pos): Boolean =
    (pos.x >= 0) && (pos.x < gridSize._1) && (pos.y >= 0) && (pos.y < gridSize._2)

  def tryTransit(s: GameState): Option[GameState] =
    val currentPieceBlocks = s.currentPiece.blocks
    if currentPieceBlocks forall s.inBounds then
      if (s.blocks intersect currentPieceBlocks).isEmpty then Some(s)
      else None
    else None

  def transit(f: Piece => Piece, onFail: => GameState = this): GameState = tryTransit(
    copy(currentPiece = f(currentPiece))
  ).getOrElse(onFail)


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


