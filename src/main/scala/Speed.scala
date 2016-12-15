package chess

sealed abstract class Speed(
    val id: Int,
    val range: Range,
    val name: String,
    val title: String) {

  lazy val key = toString.toLowerCase
}

object Speed {

  case object Bullet extends Speed(1, 0 to 179, "Bullet", "Very fast games: less than 3 minutes")
  case object Blitz extends Speed(2, 180 to 479, "Blitz", "Fast games: 3 to 8 minutes")
  case object Classical extends Speed(3, 480 to 21599, "Classical", "Classical games: more than 8 minutes")
  case object Correspondence extends Speed(4, 21600 to Int.MaxValue, "Correspondence", "Correspondence games")

  val all = List(Bullet, Blitz, Classical, Correspondence)
  val limited = List(Bullet, Blitz, Classical)

  val byId = all map { v => (v.id, v) } toMap

  def apply(id: Int): Option[Speed] = byId get id

  def apply(clock: Clock.Config) = byTime(clock.estimateTotalTime)

  def apply(clock: Option[Clock.Config]) = byTime(clock.fold(Int.MaxValue)(_.estimateTotalTime))

  def byTime(seconds: Int): Speed = all.find(_.range contains seconds) getOrElse Correspondence

  def exists(id: Int): Boolean = byId contains id
}
