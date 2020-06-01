package demozlayer

import demozlayer.counter.Counter
import demozlayer.logger.Logger
import izumi.reflect.Tag
import zio.clock.Clock
import zio.console.Console
import zio.{ clock, console, ExitCode, Has, Layer, RIO, Schedule, UIO, ULayer, URIO, URLayer, ZIO, ZLayer, ZRef }

object counter {

  type Counter = Has[Counter.Service]

  object Counter {

    trait Service {
      def nextN: UIO[Int]

      def getCount: UIO[Int]
    }

  }

  def nextN: URIO[Counter, Int] = ZIO.accessM[Counter](_.get.nextN)

  def getCount: URIO[Counter, Int] = ZIO.accessM[Counter](_.get.getCount)
}

object logger {

  type Logger = Has[Logger.Service]

  object Logger {
    trait Service {
      def info(message: => String): UIO[Unit]
      def warning(message: => String): UIO[Unit]
    }

  }

  def info(message: => String): URIO[Logger, Unit]    = ZIO.accessM[Logger](_.get.info(message))
  def warning(message: => String): URIO[Logger, Unit] = ZIO.accessM[Logger](_.get.warning(message))

}

object Logic {

  val askUserName: RIO[Console with Logger, String] =
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

  val startPrg: URIO[Logger, Unit] = logger.info("... starting ...")

  val prg: RIO[Console with Clock with Logger with Counter, Unit] =
    for {
      _    <- startPrg
      name <- askUserName
      _    <- greet(name)
      _    <- countNTimes(10)
      _    <- logger.info("stopping")
    } yield {}
}

object MyApp0 extends zio.App {

  val emptyLogger: Logger.Service = new Logger.Service {
    override def info(message: => String): UIO[Unit]    = ZIO.unit
    override def warning(message: => String): UIO[Unit] = ZIO.unit
  }

  val emptyCount: Counter.Service = new Counter.Service {
    override def nextN: UIO[Int]    = ZIO.effectTotal(0)
    override def getCount: UIO[Int] = ZIO.effectTotal(0)
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    Logic.prg.provideSome[zio.ZEnv](zenv => zenv ++ Has(emptyCount) ++ Has(emptyLogger)).exitCode
}

object MyApp1 extends zio.App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    (for {
      csl <- ZIO.access[Console](_.get)
      n   <- ZRef.make(0)

      u <- {
        val cnt = new Counter.Service {
          override def nextN: UIO[Int]    = n.updateAndGet(_ + 1)
          override def getCount: UIO[Int] = n.get
        }

        val lgr = new Logger.Service {
          override def info(message: => String): UIO[Unit] =
            for {
              n <- cnt.nextN
              _ <- csl.putStrLn(f"$n%03.0f info : $message")
            } yield {}

          override def warning(message: => String): UIO[Unit] =
            for {
              n <- cnt.nextN
              _ <- csl.putStrLn(f"$n%03.0f warn : $message")
            } yield {}
        }

        Logic.prg.provideSome[zio.ZEnv](zenv => zenv ++ Has(cnt) ++ Has(lgr))
      }

    } yield u).exitCode
}

object MyApp1Layer extends zio.App {

  val counterLayer: zio.ULayer[Counter] = ZLayer.fromEffect(
    ZRef
      .make(0)
      .map(
        ref =>
          new Counter.Service {
            override def nextN: UIO[Int]    = ref.updateAndGet(_ + 1)
            override def getCount: UIO[Int] = ref.get
          }
      )
  )

  val loggerLayer: zio.URLayer[Counter with Console, Logger] = ZLayer.fromFunction(env => {
    val cnt = env.get[Counter.Service]
    val csl = env.get[Console.Service]

    new Logger.Service {
      override def info(message: => String): UIO[Unit] =
        for {
          n <- cnt.nextN
          _ <- csl.putStrLn(f"$n%03.0f info : $message")
        } yield {}

      override def warning(message: => String): UIO[Unit] =
        for {
          n <- cnt.nextN
          _ <- csl.putStrLn(f"$n%03.0f warn : $message")
        } yield {}
    }
  })

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {

    val all = (ZLayer.identity[zio.ZEnv] ++ counterLayer) >+> loggerLayer

    Logic.prg.provideLayer(all).exitCode

  }

}

/*
 * lire une configuration depuis un fichier de configuration
 * écrire dans un fichier
 * faire un fresh sur le counter
 */
object MyApp2 extends zio.App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = ???

}
