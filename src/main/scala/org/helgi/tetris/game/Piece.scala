package org.helgi.tetris.game

import org.helgi.tetris.game.Pos.*

object Pos:
  type Pos = (Int, Int)

  extension (p: Pos)
    def x = p._1
    def y = p._2
    def +(o: Pos) = (p.x + o.x, p.y + o.y)
    def -(o: Pos) = (p.x - o.x, p.y - o.y)

enum TetrominoType:
  case I, J, L, O, S, T, Z

object TetrominoType:
  val typesNr: Int = 7

  def apply(n: Int): TetrominoType = n match
    case 0 => TetrominoType.I
    case 1 => TetrominoType.J
    case 2 => TetrominoType.L
    case 3 => TetrominoType.O
    case 4 => TetrominoType.S
    case 5 => TetrominoType.T
    case 6 => TetrominoType.Z


case class Piece(pos: Pos, kind: TetrominoType, theta: Int = 0):
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
    case TetrominoType.I => List((-1, 0), (0, 0), (1, 0), (2, 0))
    case TetrominoType.J => List((-1, 1), (-1, 0), (0, 0), (1, 0))
    case TetrominoType.L => List((-1, 0), (0, 0), (1, 0), (1, 1))
    case TetrominoType.O => List((0, 1), (0, 0), (1, 0), (1, 1))
    case TetrominoType.S => List((-1, 0), (0, 0), (0, 1), (1, 1))
    case TetrominoType.T => List((-1, 0), (0, 0), (0, 1), (1, 0))
    case TetrominoType.Z => List((-1, 1), (0, 1), (0, 0), (1, 0)))
    .map(rotateBlock).map(_ + pos)

  def rotatePos(pos: Pos): Pos =
    val c = math.cos(-theta.toDouble.toRadians).round.toInt
    val s = math.sin(-theta.toDouble.toRadians).round.toInt

    (pos.x * c - pos.y * s, pos.x * s + pos.y * c)

  def rotateBlock(block: Pos): Pos = kind match
    // handle special cases for O and I tetrominoes
    case TetrominoType.O => block
    case TetrominoType.I => theta match
      case 0 => rotatePos(block)
      case 90 => rotatePos(block) + (1, 0)
      case 180 => rotatePos(block) + (1, -1)
      case 270 => rotatePos(block) + (0, -1)
    case _ => rotatePos(block)


object Test:

  def main(args: Array[String]): Unit =
    val p = Piece((3, 4), TetrominoType.I)

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