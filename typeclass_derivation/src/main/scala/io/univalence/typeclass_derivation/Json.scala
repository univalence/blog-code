package io.univalence.typeclass_derivation

// Declare an ADT for JSON representation

sealed trait JsonValue
case class JsonNumber(value: Double) extends JsonValue
case class JsonString(value: String) extends JsonValue
case object JsonNull extends JsonValue
case class JsonArray(value: Seq[JsonValue]) extends JsonValue
case class JsonObject(value: Seq[(JsonString, JsonValue)]) extends JsonValue

object JsonValue {

  /**
    * format a JSON value on one line with JSON syntax.
    *
    * @param jsonValue JSON value to convert
    * @return String with JSON syntax representing the given JSON value.
    */
  def mkString(jsonValue: JsonValue): String =
    jsonValue match {
      case JsonNull      => "null"
      case JsonString(v) => "\"" + v + "\""
      case JsonNumber(v) => s"$v"
      case JsonArray(vs) => vs.map(mkString).mkString("[", ", ", "]")
      case JsonObject(kvs) =>
        kvs
          .map { case (k, v) => s"${mkString(k)}: ${mkString(v)}" }
          .mkString("{", ", ", "}")
    }
}

// Declaration of a typeclass to convert memory models into JSON

trait ToJson[A] {
  def toJson(a: A): JsonValue
}

object ToJson {
  import magnolia._
  import scala.language.experimental.macros

  type Typeclass[A] = ToJson[A]

  // [extension method] add toJson method to every instances of typeclass ToJson
  implicit class ToJsonOps[A: Typeclass](a: A) {
    def toJson: JsonValue = implicitly[Typeclass[A]].toJson(a)
  }

  // base insances

  implicit val intToJson: Typeclass[Int] = a => JsonNumber(a.toDouble)
  implicit val doubleToJson: Typeclass[Double] = a => JsonNumber(a)
  implicit val stringToJson: Typeclass[String] = a => JsonString(a)
  implicit def listToJson[A: Typeclass]: Typeclass[List[A]] =
    a => JsonArray(a.map(_.toJson))
  implicit def mapToJson[A: Typeclass]: Typeclass[List[(String, A)]] =
    a => JsonObject(a.map { case (k, v) => (JsonString(k), v.toJson) })

  // typeclass derivation for case classes
  def combine[A](ctx: CaseClass[Typeclass, A]): Typeclass[A] =
    a => {
      val paramMap: List[(JsonString, JsonValue)] =
        ctx.parameters
          .map(p => JsonString(p.label) -> p.typeclass.toJson(p.dereference(a)))
          .toList

      JsonObject(paramMap)
    }

  def dispatch[A](sealedTrait: SealedTrait[Typeclass, A]): Typeclass[A] =
    a =>
      sealedTrait.dispatch(a) { subtype =>
        val m =
          Seq(
            "typename" -> JsonString(subtype.typeName.short),
            "value" -> subtype.typeclass.toJson(subtype.cast(a))
          )

        JsonObject(m.map {
          case (k, v) => JsonString(k) -> v
        })
    }

  implicit def gen[A]: Typeclass[A] = macro Magnolia.gen[A]
}
