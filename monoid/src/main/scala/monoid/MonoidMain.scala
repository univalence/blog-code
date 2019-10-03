package monoid

import scala.collection.MapView
import scala.io.Source
import scala.util.Try

object MonoidMain {

  def main(args: Array[String]): Unit = {
    val file = Source.fromFile("data/geo/stations.csv")
    val partitions: MapView[String, Iterable[Double]] = getRatingsByDepartement(file.getLines().to(Iterable))

    // phase 1 (Map): get ratings only and associate the value 1 to then
    val partitionedRatingWithOne =
      partitions.mapValues(data => data.map(rating => (rating, 1)))

    // phase 2 (Combine): sum ratings and 1s for each partition
    val partitionedSumRatingsAndCount =
      partitionedRatingWithOne.mapValues(data => data.combineAll)

    // phase 3 (Reduce): combine all partitions the sum of all ratings and counts
    val (rating, count) =
      partitionedSumRatingsAndCount.values.combineAll

    println(rating / count)
  }

  trait Monoid[A] {
    def empty: A
    def combine(a: A, b: A): A
  }

  object Monoid {
    @inline def apply[A](implicit ev: Monoid[A]): Monoid[A] = ev
  }

  // Monoid (Int, 0, +)
  implicit val intMonoid: Monoid[Int] = new Monoid[Int] {
    override def empty: Int = 0
    override def combine(a: Int, b: Int): Int = a + b
  }

  // Monoid (Double, 0.0, +)
  implicit val doubleMonoid: Monoid[Double] = new Monoid[Double] {
    override def empty: Double = 0.0
    override def combine(a: Double, b: Double): Double = a + b
  }

  // turn any tuple (A, B) into Monoid, providing A and B both are Monoid
  implicit def tupleMonoid[A: Monoid, B: Monoid]: Monoid[(A, B)] =
    new Monoid[(A, B)] {
      override def empty: (A, B) = (Monoid[A].empty, Monoid[B].empty)

      override def combine(left: (A, B), right: (A, B)): (A, B) =
        (Monoid[A].combine(left._1, right._1),
          Monoid[B].combine(left._2, right._2))
    }

  implicit class iterableWithCombineAll[A: Monoid](l: Iterable[A]) {
    def combineAll: A = l.fold(Monoid[A].empty)(Monoid[A].combine)
  }

  def getRatingsByDepartement(lines: Iterable[String]): MapView[String, Iterable[Double]] = {
    val data: Iterable[String] = lines.drop(1)
    val rows: Iterable[Array[String]] =
      data.map { line =>
        // cleaning line and separate fields
        val row: Array[String] = line.trim.split(",")

        // cleansing: if fields are missing, we pad row with empty strings
        row.padTo(7, "")
      }

    val deptRatings: Iterable[(String, Double)] =
    // we remove lines
      rows.filterNot(_(6).isEmpty)
        .map(fields =>
          (fields(6), Try { fields(1).toDouble }.getOrElse(0.0))
        )

    deptRatings
      .groupBy { case (departement, rating) => departement }
      .view.mapValues(row => row.map { case (departement, rating) => rating })
  }

}
