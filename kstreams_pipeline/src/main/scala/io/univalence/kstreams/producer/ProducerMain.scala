package io.univalence.kstreams.producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

object ProducerMain {

  import scala.jdk.CollectionConverters._

  val inputStream = "text-stream"

  def main(args: Array[String]): Unit = {
    val properties =
      Map[String, AnyRef](
        "bootstrap.servers" -> "localhost:9092",
        "key.serializer" -> classOf[
          org.apache.kafka.common.serialization.StringSerializer
        ].getCanonicalName,
        "value.serializer" -> classOf[
          org.apache.kafka.common.serialization.StringSerializer
        ].getCanonicalName
      ).asJava
    val producer: KafkaProducer[String, String] = new KafkaProducer(properties)

    try {
      val text: Array[String] =
        """Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut hendrerit
          |malesuada turpis non bibendum. Integer ultricies mi non pellentesque eleifend.
          |Nulla ipsum risus, tristique ut nulla vitae, egestas elementum odio. Integer
          |non feugiat sem. Cras sed malesuada tortor, hendrerit mattis nunc. Curabitur
          |ut posuere libero, ac lacinia enim. Donec at posuere massa. Curabitur eget
          |viverra turpis. Sed sollicitudin interdum nibh, a tristique velit blandit id.
          |Donec nec molestie eros. Mauris justo turpis, pulvinar eget diam vitae,
          |malesuada sagittis sem.""".stripMargin.split("\n+")

      text.foreach { line =>
        val record: ProducerRecord[String, String] =
          new ProducerRecord(inputStream, line)

        producer.send(record)
      }
    } finally {
      producer.close()
    }
  }
}
