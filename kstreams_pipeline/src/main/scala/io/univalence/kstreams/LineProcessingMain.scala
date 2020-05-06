package io.univalence.kstreams

import io.univalence.kstreams.lib.{JavaProperties, ZKafkaStreams}
import java.util.Properties
import org.apache.kafka.streams.{KafkaStreams, Topology}
import zio._

object LineProcessingMain extends App {

  object info {
    val serviceName    = "line-processing"
    val serviceVersion = "0.1"

    val inputStream      = "text-stream"
    val outputStream     = "word-stream"
    val bootstrapServers = "localhost:9092"
  }

  lazy val topology: Topology = {
    import org.apache.kafka.streams.scala.ImplicitConversions._
    import org.apache.kafka.streams.scala.Serdes._
    import org.apache.kafka.streams.scala.StreamsBuilder

    val builder = new StreamsBuilder

    builder
      .stream[String, String](info.inputStream)
      .flatMapValues(line => line.split("[\\s.,;:!?]+"))
      .to(info.outputStream)

    builder.build()
  }

  lazy val properties: Properties = {
    import org.apache.kafka.common.serialization.Serdes
    import org.apache.kafka.streams.StreamsConfig._

    JavaProperties(
      APPLICATION_ID_CONFIG            -> info.serviceName,
      BOOTSTRAP_SERVERS_CONFIG         -> info.bootstrapServers,
      DEFAULT_KEY_SERDE_CLASS_CONFIG   -> classOf[Serdes.StringSerde],
      DEFAULT_VALUE_SERDE_CLASS_CONFIG -> classOf[Serdes.StringSerde]
    )
  }

  lazy val webService: LineProcessingWebService =
    new LineProcessingWebService(zKafkaStream, "localhost", 8080)

  lazy val zKafkaStream: ZKafkaStreams =
    new ZKafkaStreams(
      new KafkaStreams(topology, properties),
      topology.describe(),
      info.serviceName,
      info.serviceVersion
    )

  lazy val program: ZIO[ZEnv, Throwable, Unit] =
    webService.serve.fork *> zKafkaStream.serve

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    program.as(0).orDie
}
