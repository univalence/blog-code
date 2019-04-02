package io.univalence.typeclass_derivation

sealed trait FormError
case class UsernameError(message: String) extends FormError
case class PasswordError(message: String) extends FormError

case class Address(id: String, city: String)
case class Person(id: String, name: String, age: Int, address: Address)

object Main {
  import JsonValue._
  import ToJson._

  def main(args: Array[String]): Unit = {
    println(42.toJson)
    println("all work and no play makes jack a dull boy".toJson)
    println(mkString(List("a" -> 1, "b" -> 3).toJson))

    val error: FormError = UsernameError("bad username")
    println(mkString(error.toJson))
    println(mkString(UsernameError("bad username").toJson))

    val toto = Person(
      id = "p1",
      name = "Toto",
      age = 32,
      address = Address("c1", "Paris")
    )
    println(mkString(toto.toJson))
  }

}
