package org.helgi.tetris.game

import org.helgi.tetris.game.GameStatus.*
import org.helgi.tetris.game.Pos.*

import scala.annotation.tailrec
import scala.util.Random

type TetrominoProducer = LazyList[TetrominoType]

// TODO integrate into GameState
def randomStream(random: Random): TetrominoProducer =
  TetrominoType(random.nextInt(TetrominoType.typesNr)) #:: randomStream(random)

enum GameStatus:
  case Active, Over


object GameState:
  private[this] val rng = new Random()

  def init(grid: (Int, Int)): GameState =
    GameState(grid, Set.empty, Piece(startPlace(grid), randomPiece), randomPiece)

  private def startPlace(grid: (Int, Int)): Pos = (grid._1 / 2, grid._2 - 1)

  private def randomPiece: TetrominoType =
    TetrominoType(rng.nextInt(TetrominoType.typesNr))


case class GameState(grid: (Int, Int), placedBlocks: Set[Pos],
                     currentPiece: Piece, next: TetrominoType,
                     held: Option[TetrominoType] = None, canHold: Boolean = true,
                     status: GameStatus = Active, totalLinesCleared: Int = 0):

  import GameState.*

  // TODO add collision handling
  def rotate: GameState = makeMove(_.rotate)

  def left: GameState = makeMove(_.left)

  def right: GameState = makeMove(_.right)

  def tick: GameState = makeMove(_.down, { _ => afterDropState })

  def drop: GameState =
    move(_.down)
      .map(_.drop)
      .getOrElse(afterDropState)

  def hold: GameState =
    if canHold then
      held.orElse(Some(next))
        .map(Piece(startPlace(grid), _))
        .flatMap(tryToPlace(placedBlocks, _))
        .map { cp =>
          val hp = Some(currentPiece.kind)
          val np = if held.isEmpty then randomPiece else next
          copy(currentPiece = cp, next = np, held = hp, canHold = false)
        }.getOrElse(this)
    else this

  private def afterDropState: GameState =
    tryToGenerateNewPiece.map { it =>
      val (linesCleared, blocks) = clearLines(placedBlocks ++ currentPiece.blocks)
      copy(placedBlocks = blocks, currentPiece = it, next = randomPiece,
        totalLinesCleared = totalLinesCleared + linesCleared)
    }.getOrElse(copy(status = Over))

  // TODO add line clearing algorithm
  private def clearLines(blocks: Set[Pos]): (Int, Set[Pos]) = (0, blocks)

  @tailrec
  private def tryToPlace(blocks: Set[Pos], p: Piece): Option[Piece] =
    if !inBound(p.pos) then None
    else if !isColliding(blocks, p) then Some(p)
    else tryToPlace(blocks, p.copy(pos = p.pos + (0, 1)))

  private def tryToGenerateNewPiece: Option[Piece] =
    val piece = Piece(startPlace(grid), next)
    val blocks = placedBlocks ++ currentPiece.blocks
    tryToPlace(blocks, piece)

  private def inBoundOrOverTop(block: Pos): Boolean =
    block.x >= 1 && block.x <= grid._1 && block.y >= 1

  private def inBound(block: Pos): Boolean =
    inBoundOrOverTop(block) && block.y <= grid._2

  private def inBound(p: Piece): Boolean =
    p.blocks.forall(inBoundOrOverTop) &&
      p.blocks.exists(_.y <= grid._2)

  private def isColliding(blocks: Set[Pos], block: Pos): Boolean =
    blocks contains block

  private def isColliding(blocks: Set[Pos], p: Piece): Boolean =
    p.blocks.exists(isColliding(blocks, _))

  private def isColliding(p: Piece): Boolean =
    isColliding(placedBlocks, p)

  private def validatePieceLocation(p: Piece): Boolean =
    !isColliding(p) && inBound(p)

  private def checkMove(s: GameState): Option[GameState] =
    if s.validatePieceLocation(s.currentPiece) then Some(s) else None

  private def move(f: Piece => Piece): Option[GameState] = checkMove(copy(currentPiece = f(currentPiece)))

  private def makeMove(f: Piece => Piece, onFail: (=> Piece) => GameState = _ => this): GameState =
    move(f).getOrElse(onFail(f(currentPiece)))


