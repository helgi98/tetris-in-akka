package org.helgi.tetris.game

import akka.actor.Actor
import org.helgi.tetris.game.GameStatus.Active
import org.helgi.tetris.game.Pos.*

import scala.annotation.tailrec
import scala.util.Random

object Pos:
  type Pos = (Int, Int)

  extension (p: Pos)
    def x = p._1
    def y = p._2
    def +(o: Pos) = (p.x + o.x, p.y + o.y)

enum PieceKind:
  case I, J, L, O, S, T, Z

object PieceKind:
  val kindsNr: Int = 7

  def apply(n: Int): PieceKind = n match
    case 0 => PieceKind.I
    case 1 => PieceKind.J
    case 2 => PieceKind.L
    case 3 => PieceKind.O
    case 4 => PieceKind.S
    case 5 => PieceKind.T
    case 6 => PieceKind.Z


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

  def up: Piece = up(1)

  def up(times: Int): Piece = moveVertically(times)

  def moveVertically(dy: Int): Piece = copy(pos = (pos.x, pos.y + dy))

  lazy val blocks: List[Pos] = (kind match
    case PieceKind.I => List((-1, 0), (0, 0), (1, 0), (2, 0))
    case PieceKind.J => List((-1, 1), (-1, 0), (0, 0), (1, 0))
    case PieceKind.L => List((-1, 0), (0, 0), (1, 0), (1, 1))
    case PieceKind.O => List((0, 1), (0, 0), (1, 0), (1, 1))
    case PieceKind.S => List((-1, 0), (0, 0), (0, 1), (1, 1))
    case PieceKind.T => List((-1, 0), (0, 0), (0, 1), (1, 0))
    case PieceKind.Z => List((-1, 1), (0, 1), (0, 0), (1, 0))
    ).map(rotateBlock).map(_ + pos)

  def rotatePos(pos: Pos): Pos =
    val c = math.cos(-theta.toDouble.toRadians).round.toInt
    val s = math.sin(-theta.toDouble.toRadians).round.toInt

    (pos.x * c - pos.y * s, pos.x * s + pos.y * c)

  def rotateBlock(block: Pos): Pos =
    // handle special cases for O and I tetrominoes
    if kind == PieceKind.O then block
    else if kind == PieceKind.I then
      theta match {
        case 0 => rotatePos(block)
        case 90 => rotatePos(block) + (1, 0)
        case 180 => rotatePos(block) + (1, -1)
        case 270 => rotatePos(block) + (0, -1)
      }
    else
      rotatePos(block)


object Test:

  def main(args: Array[String]): Unit =
    val p = Piece((3, 4), PieceKind.I)

    println(p.rotate(4) == p)

    println(mkTetrisField(p.blocks) + "\n")
    println(mkTetrisField(p.rotate.blocks) + "\n")
    println(mkTetrisField(p.rotate(2).blocks) + "\n")
    println(mkTetrisField(p.rotate(3).blocks) + "\n")
    println(mkTetrisField(p.rotate(4).blocks) + "\n")

  def mkTetrisField(ps: Seq[Pos]): String =
    def mkIthRow(i: Int): String =
      (for j <- 1 to 10
        yield if ps.contains((j, i)) then "O" else "-").mkString

    (for i <- 20 to 1 by -1
      yield mkIthRow(i)).mkString("\n")


enum GameCommand:
  case Rotate, Left, Right, Tick, Drop, Hold

enum GameStatus:
  case Active, Over

case class GameState(gridSize: (Int, Int), placedBlocks: Set[Pos],
                     currentPiece: Piece, nextPiece: PieceKind,
                     heldPiece: Option[PieceKind] = None, canHold: Boolean = true,
                     status: GameStatus = Active):

  val rng = new Random()

  val initPos: Pos = (gridSize._1 / 2, gridSize._2 - 1)

  def rotate: GameState = makeMove(_.rotate, { rotatedPiece =>
    ???
  })

  def left: GameState = makeMove(_.left)

  def right: GameState = makeMove(_.right)

  def tick: GameState = makeMove(_.down, { _ => afterDropState })

  def drop: GameState =
    checkMove(move(_.down))
      .map(_.drop)
      .getOrElse(afterDropState)

  val afterDropState: GameState =
    tryToGenerateNewPiece.map {
      GameState(gridSize, placedBlocks ++ currentPiece.blocks,
        _, randomPiece, heldPiece)
    }.getOrElse(copy(status = GameStatus.Over))

  @tailrec
  private def tryToPlace(blocks: Set[Pos], p: Piece): Option[Piece] =
    if !inBound(p.pos) then None
    else if !isColliding(blocks, p) then Some(p)
    else tryToPlace(blocks, p.copy(pos = p.pos + (0, 1)))

  def tryToGenerateNewPiece: Option[Piece] =
    val piece = Piece(initPos, nextPiece)
    val blocks = placedBlocks ++ currentPiece.blocks

    tryToPlace(blocks, piece)

  def hold: GameState =
    if canHold then
      heldPiece.orElse(Some(nextPiece))
        .map(Piece(initPos, _))
        .flatMap(tryToPlace(placedBlocks, _)).map { cp =>
        val hp = Some(currentPiece.kind)
        val np = randomPiece
        copy(currentPiece = cp, nextPiece = np, heldPiece = hp, canHold = false)
      }.getOrElse(this)
    else this

  def randomPiece: PieceKind = PieceKind(rng.nextInt(PieceKind.kindsNr))

  def inBoundOrOverTop(block: Pos): Boolean =
    (block.x >= 1) && (block.x <= gridSize._1) && (block.y >= 1)

  def inBound(block: Pos): Boolean =
    inBoundOrOverTop(block) && (block.y <= gridSize._2)

  def inBound(p: Piece): Boolean =
    p.blocks.forall(inBoundOrOverTop) &&
      p.blocks.exists(inBound)

  def isColliding(blocks: Set[Pos], block: Pos): Boolean =
    blocks.contains(block)

  def isColliding(blocks: Set[Pos], p: Piece): Boolean =
    p.blocks.exists(isColliding(blocks, _))

  def isColliding(p: Piece): Boolean =
    isColliding(placedBlocks, p)

  def validatePieceLocation(p: Piece): Boolean =
    !isColliding(p) && inBound(p)

  def checkMove(s: GameState): Option[GameState] =
    if s.validatePieceLocation(s.currentPiece) then Some(s) else None

  def tryMove(f: Piece => Piece): Option[GameState] =
    checkMove(move(f))

  def makeMove(f: Piece => Piece, onFail: (=> Piece) => GameState = _ => this): GameState =
    val newCurrentPiece = f(currentPiece)
    checkMove(move(newCurrentPiece)).getOrElse(onFail(newCurrentPiece))

  def move(f: Piece => Piece): GameState = move(f(currentPiece))

  def move(newCurrentPiece: Piece): GameState = copy(currentPiece = newCurrentPiece)


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


