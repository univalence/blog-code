package io.univalence.kstreams.lib

import java.util.concurrent.TimeUnit
import org.apache.kafka.streams.{KafkaStreams, TopologyDescription}
import zio.clock.Clock
import zio.duration.Duration
import zio.{UIO, ZIO}

class ZKafkaStreams(
    streams: KafkaStreams,
    topologyDescription: TopologyDescription,
    serviceName: String,
    serviceVersion: String,
    stateCheckingDelay: Duration = Duration(500, TimeUnit.MILLISECONDS)
) extends WrapMutable[KafkaStreams](streams) {

  private val stateIsUp: KafkaStreams.State => Boolean =
    state => state.isRunning || state == KafkaStreams.State.CREATED

  def serve: ZIO[Clock, Throwable, Unit] = {
    (execute(_.cleanUp())
      *> execute(_.start)
      *> execute(_.state())
        .delay(stateCheckingDelay)
        .doWhile(stateIsUp))
      .ensuring(executeTotal(_.close))
      .unit
  }

  def status: UIO[KafkaStreams.State] = executeTotal(_.state())

  def topology: UIO[TopologyDescription] = UIO(topologyDescription)

  def name: UIO[String] = UIO(serviceName)

  def version: UIO[String] = UIO(serviceVersion)

}
