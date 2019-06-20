package io.univalence.scala_2_13

import java.io._
import scala.util.{Try, Using}

object UsingMain {

  def main(args: Array[String]): Unit = {
    val file: File = File.createTempFile("using", "usg")

    val content1: Try[String] =
      for {
        _ <- Using(new BufferedWriter(new FileWriter(file))) {
          _.write("Hello world")
        }

        c <- Using(new BufferedReader(new FileReader(file))) {
          _.readLine()
        }
      } yield c

    println(s"file content 1: $content1") // print file content 1: Success(Hello world)

    val content2: Try[String] =
      for {
        _ <- Using(new BufferedWriter(new FileWriter(file))) {
          _.write("Hello world")
        }

        c <- Using(new BufferedReader(new FileReader(file))) { f =>
          f.close() // in a view to have an exception ;)
          f.readLine()
        }
      } yield c

    println(s"file content 2: $content2") // file content 2: Failure(java.io.IOException: Stream closed)
  }

}
