package chess

import format.Uci
import scala.concurrent.duration._

case class Move(
    piece: Piece,
    orig: Pos,
    dest: Pos,
    before: Board,
    after: Board,
    capture: Option[Pos],
    promotion: Option[PromotableRole],
    castle: Option[((Pos, Pos), (Pos, Pos))],
    enpassant: Boolean,
    lag: FiniteDuration = 0.millis) {

  def situationBefore = before situationOf piece.color
  def situationAfter = finalizeAfter situationOf !piece.color

  def withHistory(h: History) = copy(after = after withHistory h)

  def finalizeAfter: Board = {
    val board = after updateHistory { h1 =>
      // last move
      val h2 = h1.copy(lastMove = Some(toUci))

      // my broken castles
      if ((piece is King) && h2.canCastle(color).any)
        h2 withoutCastles color
      else if (piece is Rook) (for {
        kingPos ← after kingPosOf color
        side ← Side.kingRookSide(kingPos, orig)
        if h2 canCastle color on side
      } yield h2.withoutCastle(color, side)) getOrElse h2
      else h2
    } fixCastles

    board.variant.finalizeBoard(board, toUci, capture flatMap before.apply) updateHistory { h =>
      // Update position hashes last, only after updating the board,
      // castling rights and en-passant rights.
      h.copy(positionHashes = board.variant.updatePositionHashes(board, this, h.positionHashes))
    }
  }

  def applyVariantEffect: Move = before.variant addVariantEffect this

  def afterWithLastMove = after.copy(
    history = after.history.withLastMove(toUci))

  // does this move capture an opponent piece?
  def captures = capture.isDefined

  def promotes = promotion.isDefined

  def castles = castle.isDefined

  def normalizeCastle = castle.fold(this) {
    case (_, (rookOrig, _)) => copy(dest = rookOrig)
  }

  def color = piece.color

  def withPromotion(op: Option[PromotableRole]): Option[Move] =
    op.fold(Option(this)) { p =>
      if ((after count color.queen) > (before count color.queen)) for {
        b2 ← after take dest
        b3 ← b2.place(color - p, dest)
      } yield copy(after = b3, promotion = Some(p))
      else Some(this)
    }

  def withAfter(newBoard: Board) = copy(after = newBoard)

  def withLag(l: FiniteDuration) = copy(lag = l)

  def toUci = Uci.Move(orig, dest, promotion)

  override def toString = s"$piece ${toUci.uci}"
}
