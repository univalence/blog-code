package io.univalence.kstreams

import io.univalence.kstreams.lib._
import io.univalence.kstreams.lib.endpoints._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import zio._
import zio.interop.catz._

class LineProcessingWebService(zKafkaStreams: ZKafkaStreams, host: String, port: Int)
    extends ZKafkaStreamsWebService(host, port) {

  override val router: HttpRoutes[Task] =
    Router(
      "/monitoring/healthCheck" ->
        HealthCheckService.webService(zKafkaStreams),
      "/monitoring/topology" -> TopologyService.webService(zKafkaStreams),
      "/monitoring/info"     -> ServiceInfoService.webService(zKafkaStreams)
    )

}
