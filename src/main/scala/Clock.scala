package chess

import scala.concurrent.duration._

// All durations are expressed in seconds
sealed trait Clock {
  val config: Clock.Config
  val color: Color
  val whiteTime: Float
  val blackTime: Float
  val timerOption: Option[Double]

  def limit = config.limit
  def increment = config.increment

  def time(c: Color) = c.fold(whiteTime, blackTime)

  def outoftime(c: Color) = remainingTime(c) == 0

  def outoftimeWithGrace(c: Color, graceMillis: Int) =
    millisSinceFlag(c).exists(graceMillis.min(Clock.maxGraceMillis).<)

  def remainingTime(c: Color) = math.max(0, limit - elapsedTime(c))

  private def millisSinceFlag(c: Color): Option[Int] = (limit - elapsedTime(c)) match {
    case s if s <= 0 => Some((s * -1000).toInt)
    case _           => None
  }

  def remainingTimes = Color.all map { c => c -> remainingTime(c) } toMap

  def elapsedTime(c: Color) = time(c)

  def limitInMinutes = config.limitInMinutes

  def estimateTotalIncrement = config.estimateTotalIncrement

  def estimateTotalTime = config.estimateTotalTime

  // Emergency time cutoff, in seconds.
  def emergTime = config.emergTime

  def stop: PausedClock

  def addTime(c: Color, t: Float): Clock

  def giveTime(c: Color, t: Float): Clock

  def berserk(c: Color): Clock

  def show = config.show

  def showTime(t: Float) = {
    val hours = math.floor(t / 3600).toInt
    val minutes = math.floor((t - hours * 3600) / 60).toInt
    val seconds = t.toInt % 60
    s"${if (hours > 0) hours else ""}:$minutes:$seconds"
  }

  def moretimeable(c: Color) = remainingTime(c) < 60 * 60 * 2

  def isRunning = timerOption.isDefined

  def isInit = elapsedTime(White) == 0 && elapsedTime(Black) == 0

  def switch: Clock

  def takeback: Clock

  def reset = Clock(config)

  protected def now = System.currentTimeMillis / 1000d
}

case class RunningClock(
    config: Clock.Config,
    color: Color,
    whiteTime: Float,
    blackTime: Float,
    whiteBerserk: Boolean,
    blackBerserk: Boolean,
    timer: Double) extends Clock {

  val timerOption = Some(timer)

  override def elapsedTime(c: Color) = time(c) + {
    if (c == color) now - timer else 0
  }.toFloat

  def incrementOf(c: Color) =
    if (c.fold(whiteBerserk, blackBerserk)) 0 else increment

  def step(lag: FiniteDuration = 0.millis) = {
    val t = now
    val spentTime = (t - timer).toFloat
    val lagSeconds = lag.toMillis.toFloat / 1000
    val lagCompensation = lagSeconds min Clock.maxLagToCompensate max 0
    addTime(
      color,
      (math.max(0, spentTime - lagCompensation) - incrementOf(color))
    ).copy(
        color = !color,
        timer = t)
  }

  def stop = PausedClock(
    config = config,
    color = color,
    whiteTime = whiteTime + (if (color == White) (now - timer).toFloat else 0),
    blackTime = blackTime + (if (color == Black) (now - timer).toFloat else 0),
    whiteBerserk = whiteBerserk,
    blackBerserk = blackBerserk)

  def addTime(c: Color, t: Float): RunningClock = c match {
    case White => copy(whiteTime = whiteTime + t)
    case Black => copy(blackTime = blackTime + t)
  }

  def giveTime(c: Color, t: Float): RunningClock = addTime(c, -t)

  def berserk(c: Color): RunningClock = addTime(c, Clock.berserkPenalty(this, color)).copy(
    whiteBerserk = whiteBerserk || c.white,
    blackBerserk = blackBerserk || c.black)

  def switch: RunningClock = copy(color = !color)

  def takeback: RunningClock = {
    val t = now
    val spentTime = (t - timer).toFloat
    addTime(color, spentTime).copy(
      color = !color,
      timer = t)
  }
}

case class PausedClock(
    config: Clock.Config,
    color: Color,
    whiteTime: Float,
    blackTime: Float,
    whiteBerserk: Boolean,
    blackBerserk: Boolean) extends Clock {

  val timerOption = None

  def stop = this

  def addTime(c: Color, t: Float): PausedClock = c match {
    case White => copy(whiteTime = whiteTime + t)
    case Black => copy(blackTime = blackTime + t)
  }

  def giveTime(c: Color, t: Float): PausedClock = addTime(c, -t)

  def berserk(c: Color): PausedClock = addTime(c, Clock.berserkPenalty(this, color)).copy(
    whiteBerserk = c.fold(true, whiteBerserk),
    blackBerserk = c.fold(blackBerserk, true))

  def switch: PausedClock = copy(color = !color)

  def takeback: PausedClock = switch

  def start = RunningClock(
    config = config,
    color = color,
    whiteTime = whiteTime,
    blackTime = blackTime,
    whiteBerserk = whiteBerserk,
    blackBerserk = blackBerserk,
    timer = now)
}

object Clock {

  // All durations are expressed in seconds
  case class Config(limit: Int, increment: Int) {

    def show = s"${Clock.showLimit(limit)}+$increment"

    def limitInMinutes = limit / 60d

    def estimateTotalIncrement = 40 * increment

    def estimateTotalTime = limit + estimateTotalIncrement

    // Emergency time cutoff, in seconds.
    def emergTime = math.min(60, math.max(10, limit / 8))

    def hasIncrement = increment > 0

    def berserkable = increment == 0 || limit > 0

    def toClock = Clock(this)

    override def toString = show
  }

  val minInitLimit = 3
  // no more than this time will be offered to the lagging player
  val maxLagToCompensate = 1f
  // no more than this time to get the last move in
  val maxGraceMillis = 1000

  def apply(limit: Int, increment: Int): PausedClock = apply(Config(limit, increment))

  def apply(config: Config): PausedClock = {
    val clock = PausedClock(
      config = config,
      color = White,
      whiteTime = 0f,
      blackTime = 0f,
      whiteBerserk = false,
      blackBerserk = false)
    if (clock.limit == 0) clock
      .giveTime(White, config.increment.max(minInitLimit))
      .giveTime(Black, config.increment.max(minInitLimit))
    else clock
  }

  def showLimit(limit: Int) = limit match {
    case l if l % 60 == 0 => l / 60
    case 30               => "½"
    case 45               => "¾"
    case 90               => "1.5"
    case _                => limit.toString
  }

  private[chess] def berserkPenalty(clock: Clock, color: Color): Int = {
    val incTime = clock.estimateTotalIncrement
    val iniTime = clock.limit
    if (iniTime < incTime) 0 else iniTime / 2
  }.toInt

  def timeString(t: Int) = s"${t}s"
}
