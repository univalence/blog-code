package io.univalence.kstreams.lib

import org.http4s.HttpRoutes
import org.http4s.server.blaze.BlazeServerBuilder
import zio.{Task, ZEnv, ZIO}
import zio.interop.catz._
import zio.interop.catz.implicits._

abstract class ZKafkaStreamsWebService(host: String, port: Int) {
  import org.http4s.implicits._

  val router: HttpRoutes[Task]

  final def serve: ZIO[ZEnv, Throwable, Unit] =
    ZIO
      .runtime[ZEnv]
      .flatMap { implicit rts =>
        BlazeServerBuilder[Task]
          .bindHttp(port, host)
          .withHttpApp(router.orNotFound)
          .serve
          .compile
          .drain
      }

}
