package chess
package variant

import format.Uci

case object Crazyhouse extends Variant(
  id = 10,
  key = "crazyhouse",
  name = "Crazyhouse",
  shortName = "crazy",
  title = "Captured pieces can be dropped back on the board instead of moving a piece.",
  standardInitialPosition = true) {

  private def canDropPawnOn(pos: Pos) = (pos.y != 1 && pos.y != 8)

  override def drop(situation: Situation, role: Role, pos: Pos): Valid[Drop] = for {
    d1 <- ((situation.board.crazyData) match {
      case Some(d1) => success(d1)
      case None => failure("Board has no crazyhouse data")
    })
    _ <- (if (role != Pawn || canDropPawnOn(pos)) success(d1) else failure(s"Can't drop $role on $pos"))
    piece = Piece(situation.color, role)
    d2 <- ((d1.drop(piece, pos)) match {
      case Some(d2) => success(d2)
      case None => failure(s"No $piece to drop")
    })
    board1 <- ((situation.board.place(piece, pos)) match {
      case Some(board1) => success(board1)
      case None => failure(s"Can't drop $role on $pos, it's occupied")
    })
    _ <- (if (!board1.check(situation.color)) success(board1) else failure(s"Dropping $role on $pos doesn't uncheck the king"))
  } yield Drop(
    piece = piece,
    pos = pos,
    before = situation.board,
    after = board1 withCrazyData d2)

  override def updatePositionHashes(board: Board, move: Move, hash: chess.PositionHash) =
    updateHashes(hash, board, !move.piece.color)

  override def updatePositionHashes(board: Board, drop: Drop, hash: chess.PositionHash) =
    updateHashes(hash, board, !drop.piece.color)

  // don't clear the hash on pawn move or promotion, to preserve threefold repetition
  // but disable 50-moves by truncating the hash at 99
  private def updateHashes(hash: PositionHash, board: Board, color: Color) = {
    val newHash = Hash(Situation(board, color)) ++ hash
    if (newHash.size > 99 * Hash.size) newHash take 99 * Hash.size else newHash
  }

  override def finalizeBoard(board: Board, uci: Uci, capture: Option[Piece]): Board = uci match {
    case Uci.Move(orig, dest, promOption) =>
      board.crazyData.fold(board) { data =>
        val d1 = capture.fold(data) { data.store(_, dest) }
        val d2 = promOption.fold(d1.move(orig, dest)) { _ => d1 promote dest }
        board withCrazyData d2
      }
    case _ => board
  }

  private def canDropStuff(situation: Situation) =
    situation.board.crazyData.fold(false) { (data: Data) =>
      val roles = data.pockets(situation.color).roles
      roles.nonEmpty && possibleDrops(situation).fold(true) { squares =>
        squares.nonEmpty && {
          squares.exists(canDropPawnOn) || roles.exists(chess.Pawn !=)
        }
      }
    }

  override def staleMate(situation: Situation) =
    super.staleMate(situation) && !canDropStuff(situation)

  override def checkmate(situation: Situation) =
    super.checkmate(situation) && !canDropStuff(situation)

  def possibleDrops(situation: Situation): Option[List[Pos]] =
    if (!situation.check) None
    else situation.kingPos.map { blockades(situation, _) }

  private def blockades(situation: Situation, kingPos: Pos): List[Pos] = {
    def attacker(piece: Piece) = piece.role.projection && piece.color != situation.color
    def forward(p: Pos, dir: Direction, squares: List[Pos]): List[Pos] = dir(p) match {
      case None => Nil
      case Some(next) if situation.board(next).exists(attacker) => next :: squares
      case Some(next) if situation.board(next).isDefined => Nil
      case Some(next) => forward(next, dir, next :: squares)
    }
    Queen.dirs flatMap { forward(kingPos, _, Nil) } filter { square =>
      situation.board.place(Piece(situation.color, Knight), square) exists { defended =>
        !defended.check(situation.color)
      }
    }
  }

  val storableRoles = List(Pawn, Knight, Bishop, Rook, Queen)

  case class Data(
      pockets: Pockets,
      // in crazyhouse, a promoted piece becomes a pawn
      // when captured and put in the pocket.
      // there we need to remember which pieces are issued from promotions.
      // we do that by tracking their positions on the board.
      promoted: Set[Pos]) {

    def drop(piece: Piece, pos: Pos): Option[Data] =
      pockets take piece map { nps =>
        copy(pockets = nps)
      }

    def store(piece: Piece, from: Pos) =
      copy(
        pockets = pockets store (if (promoted(from)) piece.color.pawn else piece),
        promoted = promoted - from)

    def promote(pos: Pos) = copy(promoted = promoted + pos)

    def move(orig: Pos, dest: Pos) = copy(
      promoted = if (promoted(orig)) promoted - orig + dest else promoted
    )
  }

  object Data {
    val init = Data(Pockets(Pocket(Nil), Pocket(Nil)), Set.empty)
  }

  case class Pockets(white: Pocket, black: Pocket) {

    def apply(color: Color) = color.fold(white, black)

    def take(piece: Piece): Option[Pockets] = piece.color.fold(
      white take piece.role map { np => copy(white = np) },
      black take piece.role map { np => copy(black = np) })

    def store(piece: Piece) = copy(
      white = piece.color.fold(white, white store piece.role),
      black = piece.color.fold(black store piece.role, black))
  }

  case class Pocket(roles: List[Role]) {

    def take(role: Role) =
      if (roles contains role) Some(copy(roles = roles diff List(role)))
      else None

    def store(role: Role) =
      if (storableRoles contains role) copy(roles = role :: roles)
      else this
  }
}
