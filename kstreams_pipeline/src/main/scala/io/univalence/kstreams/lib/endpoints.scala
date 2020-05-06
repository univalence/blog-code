package io.univalence.kstreams.lib

import java.time.ZonedDateTime
import org.http4s.MediaType._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import zio.interop.catz._
import zio.{Task, ZIO}

object endpoints {
  object ServiceInfoService {

    private val dsl = Http4sDsl[Task]
    import dsl._

    def webService(streams: ZKafkaStreams): HttpRoutes[Task] =
      HttpRoutes
        .of[Task] {
          case GET -> Root =>
            val response =
              for {
                name    <- streams.name
                version <- streams.version
              } yield ServiceInfoResponse(
                timestamp = ZonedDateTime.now,
                serviceName = name,
                serviceVersion = version
              ).toJson

            Ok(response).map(
              _.withContentType((`Content-Type`(application.json)))
            )
        }
  }

  case class ServiceInfoResponse(timestamp: ZonedDateTime, serviceName: String, serviceVersion: String) {
    def toJson: String =
      s"""{ "timestamp": "${timestamp.toString}", "serviceName": "$serviceName", "serviceVersion": "$serviceVersion" }"""
  }

  /**
    * This endpoint can be used with Conduktor or https://zz85.github.io/kafka-streams-viz/
    */
  object TopologyService {
    private val dsl = Http4sDsl[Task]
    import dsl._

    def webService(streams: ZKafkaStreams): HttpRoutes[Task] =
      HttpRoutes
        .of[Task] {
          case GET -> Root =>
            val response: ZIO[Any, Nothing, String] =
              streams.topology.map(_.toString)

            Ok(response).map(_.withContentType((`Content-Type`(text.plain))))
        }
  }

  object HealthCheckService {
    private val dsl = Http4sDsl[Task]
    import dsl._

    def webService(streams: ZKafkaStreams): HttpRoutes[Task] =
      HttpRoutes
        .of[Task] {
          case GET -> Root =>
            streams.status.flatMap { s =>
              if (s.isRunning) Ok()
              else ServiceUnavailable()
            }
        }
  }

}
