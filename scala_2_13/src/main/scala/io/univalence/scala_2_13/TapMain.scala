package io.univalence.scala_2_13

import scala.util.chaining._

object TapMain {

  def main(args: Array[String]): Unit = {
    val result1 = "hello".tap(println) + " world" // print hello
    val result2 = "hello" + " world"

    println(result1 == result2) // print true
  }

}
