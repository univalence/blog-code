package io.univalence.scala_2_13

object PatternMatchingInterpolatorMain {

  def main(args: Array[String]): Unit = {
    val date = "2000-01-01"
    val s"$year-$month-$day" = date

    println(s"year = $year")
    println(s"month = $month")
    println(s"day = $day")

    println(dateComponentOf(date))
    println(dateComponentOf("01/01/2000"))
    println(dateComponentOf("01 01 2000"))
  }

  def dateComponentOf(date: String): Option[(Int, Int, Int)] =
    date match {
      case s"$year-$month-$day" => Option((year.toInt, month.toInt, day.toInt))
      case s"$day/$month/$year" => Option((year.toInt, month.toInt, day.toInt))
      case _ => None
    }

}
