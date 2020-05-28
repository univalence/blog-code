package demozlayer

import demozlayer.counter.Counter
import demozlayer.logger.Logger
import zio.clock.Clock
import zio.console.Console
import zio.{ clock, console, ExitCode, Has, RIO, Schedule, UIO, URIO, ZIO, ZLayer }

object counter {
  type Counter = Has[Service]

  trait Service {
    def nextN: UIO[Int]
    def getCount: UIO[Int]
  }

  def nextN: URIO[Counter, Int]    = ZIO.accessM[Counter](_.get.nextN)
  def getCount: URIO[Counter, Int] = ZIO.accessM[Counter](_.get.getCount)

}

object logger {
  type Logger = Has[Service]

  trait Service {
    def info(message: => String): UIO[Unit]
    def warning(message: => String): UIO[Unit]
  }

  def info(message: => String): URIO[Logger, Unit]    = ZIO.accessM[Logger](_.get.info(message))
  def warning(message: => String): URIO[Logger, Unit] = ZIO.accessM[Logger](_.get.warning(message))
}

object Logic {

  def askUserName: RIO[Console with Logger, String] =
    for {
      _    <- console.putStrLn("Hello User, what is your name ? ")
      name <- console.getStrLn
      _    <- logger.info(s"user = $name")
    } yield name

  def greet(name: String): RIO[Console with Clock, Unit] =
    for {
      dt <- clock.currentDateTime
      _  <- console.putStrLn(s"Hello $name, it is $dt !")
    } yield {}

  def countNTimes(n: Int): ZIO[Console with Counter, Nothing, Int] =
    (counter.nextN >>= (i => zio.console.putStrLn(s"N = $i"))).repeat(Schedule.recurs(n))

  def startPrg: URIO[Logger, Unit] = logger.info("... starting ...")

  def prg: RIO[Console with Clock with Logger with Counter, Unit] =
    for {
      _    <- startPrg
      name <- askUserName
      _    <- greet(name)
      _    <- countNTimes(10)
    } yield {}
}

object MyApp extends zio.App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    val layer: ZLayer[zio.ZEnv, Throwable, Logger with Counter] = ???

    Logic.prg.provideCustomLayer(layer).exitCode
  }
}
