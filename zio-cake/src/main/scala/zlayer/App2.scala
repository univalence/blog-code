package zlayer.app2

import java.io.{ File, FileOutputStream, PrintWriter }
import java.util.UUID

import izumi.reflect.Tag
import zio.clock.Clock
import zio.console.Console
import zio.{ clock, console, ExitCode, Has, Managed, RIO, Tagged, Task, UIO, URIO, ZIO, ZLayer, ZManaged }
import zlayer.app2.log.Log

class IR[X <: AutoCloseable] private (x: X) {
  def execute[B](f: X => B): Task[B] = Task(f(x))
  val close: Task[Unit]              = execute(_.close())
}

object IR {
  def apply[X <: AutoCloseable](x: => X): Task[IR[X]] =
    Task(new IR(x))
}

package object log {
  type Log = Has[Log.Service]

  object Log {
    trait Service {
      def info(message: => String): UIO[Unit]
      def warning(message: => String): UIO[Unit]
    }

    val noLog: Service = new Service {
      override def info(message: => String): UIO[Unit]    = ZIO.unit
      override def warning(message: => String): UIO[Unit] = ZIO.unit
    }

    def onFile: ZManaged[Clock, Throwable, Service] = {
      val getWriter: Managed[Throwable, IR[PrintWriter]] =
        ZManaged.make(IR(new PrintWriter(new FileOutputStream(new File("log.txt"), true))))(_.close.ignore)

      val get = for {
        clock  <- ZIO.environment[Clock].map(_.get[Clock.Service]).toManaged_
        id     <- ZManaged.effectTotal(UUID.randomUUID().toString.take(4))
        writer <- getWriter
      } yield {
        new Log.Service {
          private def log(level: String, message: String): UIO[Unit] =
            (for {
              dt <- clock.currentDateTime
              _  <- writer.execute(_.println(s"[$level] $dt $id : $message"))
            } yield {}).ignore

          override def info(message: => String): UIO[Unit]    = log("info", message)
          override def warning(message: => String): UIO[Unit] = log("warning", message)
        }
      }
      get
        .tap(_.info("starting logger").toManaged_)
        .onExitFirst(exit => exit.foldM(failed = _ => ZIO.unit, completed = service => service.info("stopped logger")))
    }
  }

  def info(message: => String): URIO[Log, Unit]    = ZIO.accessM(_.get.info(message))
  def warning(message: => String): URIO[Log, Unit] = ZIO.accessM(_.get.warning(message))

}

object PureAppLayer extends zio.App {
  def askUserName: RIO[Console with Log, String] =
    for {
      _    <- console.putStrLn("Hello User, what is your name ? ")
      name <- console.getStrLn
      _    <- log.info(s"user = $name")
    } yield name

  def greet(name: String): RIO[Console with Clock, Unit] =
    for {
      dt <- clock.currentDateTime
      _  <- console.putStrLn(s"Hello $name, it is $dt !")
    } yield {}

  def startPrg: URIO[Log, Unit] = log.info("... starting ...")

  def prg: RIO[Console with Clock with Log, Unit] =
    for {
      _    <- startPrg
      name <- askUserName
      _    <- greet(name)
    } yield {}

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    val main: URIO[Console with Clock with Log, ExitCode] = prg.exitCode

    //composition horizontale de Has
    main.provideSome[zio.ZEnv](zEnv => zEnv add Log.noLog)

    //managed layer
    val logLayer: ZLayer[Clock, Throwable, Log] = ZLayer(Log.onFile.map(Has.apply))

    main.provideCustomLayer(logLayer).orDie
    main.provideSomeLayer[zio.ZEnv](logLayer).orDie

    //composition horizontale de ZLayer
    val envLayer: ZLayer[Clock with Console, Nothing, Clock with Console] = ZLayer.identity[Clock with Console]

    val allLayer: ZLayer[Clock with Console, Throwable, Clock with Console with Log] = envLayer ++ logLayer

    main.provideLayer(allLayer).orDie

    trait A {
      def tick: UIO[Unit]
    }
    val tick = ZIO.accessM[Has[A]](_.get.tick)

    trait B

    val serviceA: ZIO[Log, Nothing, A] = {
      log.info("get service A") *> ZIO.access[Log](
        log =>
          new A {
            override def tick: UIO[Unit] = log.get.info("tick A")
          }
      )
    }
    val serviceB: ZIO[Log, Nothing, B] = log.info("get service B") *> UIO(new B {})

    def fromZIO[R, E, A: Tag](zio: ZIO[R, E, A]): ZLayer[R, E, Has[A]] =
      ZLayer(zio.map(x => Has(x)).toManaged_)

    val layerA: ZLayer[Log, Nothing, Has[A]] = fromZIO(serviceA)
    val layerB: ZLayer[Log, Nothing, Has[B]] = fromZIO(serviceB)

    //Par le mécanisme de Mémoisation, le
    val layers: ZLayer[Clock, Throwable, Has[A] with Has[B] with Log] =
      (logLayer >>> layerA) ++ (logLayer >>> layerB) ++ logLayer

    val layers1: ZLayer[Clock, Throwable, Has[A] with Has[B] with Log] =
      logLayer >>> (layerA ++ layerB ++ ZLayer.identity[Log])

    logLayer orElse ZLayer.succeed(Log.noLog)

    //logLayer.fold(ZLayer.succeed(Log.noLog), ZLayer.identity)

    {
      val main = log.info("HELLO") *> tick
      main.provideCustomLayer(layers).as(0).orDie
    }

    ???
  }

}
