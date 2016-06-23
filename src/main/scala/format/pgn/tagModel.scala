package chess
package format.pgn

case class Tag(name: TagType, value: String) {

  override def toString = """[%s "%s"]""".format(name, value)
}

sealed trait TagType {
  lazy val name = toString
  lazy val lowercase = name.toLowerCase
}

object Tag {

  case object Event extends TagType
  case object Site extends TagType
  case object Date extends TagType
  case object Round extends TagType
  case object White extends TagType
  case object Black extends TagType
  case object TimeControl extends TagType
  case object WhiteClock extends TagType
  case object BlackClock extends TagType
  case object WhiteElo extends TagType
  case object BlackElo extends TagType
  case object WhiteTitle extends TagType
  case object BlackTitle extends TagType
  case object Result extends TagType
  case object FEN extends TagType
  case object Variant extends TagType
  case object ECO extends TagType
  case object Opening extends TagType
  case object Termination extends TagType
  case object Annotator extends TagType
  case class Unknown(n: String) extends TagType {
    override def toString = n
  }

  val tagTypes = List(
    Event, Site, Date, Round, White, Black, TimeControl,
    WhiteClock, BlackClock, WhiteElo, BlackElo, WhiteTitle, BlackTitle,
    Result, FEN, Variant, ECO, Opening, Termination, Annotator)
  val tagTypesByLowercase = tagTypes map { t => t.lowercase -> t } toMap

  def apply(name: String, value: Any): Tag = new Tag(
    name = tagType(name),
    value = value.toString)

  def apply(name: Tag.type => TagType, value: Any): Tag = new Tag(
    name = name(this),
    value = value.toString)

  def tagType(name: String) =
    (tagTypesByLowercase get name.toLowerCase) getOrElse Unknown(name)

  def find(tags: List[Tag], name: String): Option[String] = {
    val tagType = (Tag tagType name)
    tags.find(_.name == tagType).map(_.value)
  }
}
