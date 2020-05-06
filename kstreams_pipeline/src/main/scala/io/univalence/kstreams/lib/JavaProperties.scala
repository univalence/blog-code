package io.univalence.kstreams.lib

import java.util.Properties

object JavaProperties {
  import scala.jdk.CollectionConverters._ // scala 2.13

  def apply(properties: (String, AnyRef)*): Properties = {
    val prop = new Properties()
    prop.putAll(properties.toMap.asJava)

    prop
  }
}
