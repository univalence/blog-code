package demozlayer

import java.io.{ File, FileOutputStream, PrintWriter }

import demozlayer.counter.Counter
import demozlayer.logger.Logger
import zio.clock.Clock
import zio.console.Console
import zio._

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
    (counter.nextN >>= (i => zio.console.putStrLn(s"N = $i"))).repeat(Schedule.recurs(n - 1))

  val startPrg: URIO[Logger, Unit] = logger.info("... starting ...")

  def prg(n: Int): RIO[Console with Clock with Logger with Counter, Unit] =
    for {
      _    <- startPrg
      name <- askUserName
      _    <- greet(name)
      _    <- countNTimes(n)
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

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val prg: RIO[Console with Clock with Logger with Counter, Unit] = Logic.prg(10)
    //type ZEnv = Clock with Console with System with Random with Blocking
    val provided: RIO[zio.ZEnv, Unit] = prg.provideSome[zio.ZEnv](zenv => zenv ++ Has(emptyCount) ++ Has(emptyLogger))

    //URIO[zio.ZEnv, ExitCode]
    provided.exitCode
  }
}

object Types {

  type Task[+A]     = ZIO[Any, Throwable, A] // Returns `A`, can fails with `Throwable`, depends on nothing (`Any`).
  type RIO[-R, +A]  = ZIO[R, Throwable, A]   // Like `Task`, but depends on `R`.
  type UIO[+A]      = ZIO[Any, Nothing, A]   // Always returns A, never fails (`Nothing`), depends on nothing (`Any`).
  type URIO[-R, +A] = ZIO[R, Nothing, A]     // Like `UIO`, always return `A`, never fails, depends on `R`.
  type IO[+E, +A]   = ZIO[Any, E, A]         // Returns `A`, can fails with `E` .
}

object MyApp1 extends zio.App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
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
              _ <- csl.putStrLn(f"$n%03d info : $message")
            } yield {}

          override def warning(message: => String): UIO[Unit] =
            for {
              n <- cnt.nextN
              _ <- csl.putStrLn(f"$n%03d warn : $message")
            } yield {}
        }

        Logic.prg(n = 10).provideSome[zio.ZEnv](zenv => zenv ++ Has(cnt) ++ Has(lgr))
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

    Logic.prg(n = 10).provideLayer(all).exitCode

  }

}

/*
 * lire une configuration depuis un fichier de configuration
 * écrire dans un fichier
 * faire un fresh sur le counter
 */

case class Config(logPath: String, nTimes: Int)

object MyApp2 extends zio.App {

  val counterLayer: ULayer[Counter] = ZLayer.fromEffect(
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

  val configLayer: RLayer[Console with system.System, Has[Config]] = ZLayer.fromEffect(for {
    path   <- zio.system.env("logPath")
    nTimes <- zio.system.env("nTimes")
    //because toInt
    config <- ZIO(Config(path.getOrElse("log.txt"), nTimes.map(_.toInt).getOrElse(10)))
    _      <- zio.console.putStrLn(s"using $config")
  } yield config)

  val loggerLayer: ZLayer[Has[Config] with Clock with Counter, Throwable, Logger] = {
    def newPrinter(path: String): Task[PrintWriter] = ZIO(new PrintWriter(new FileOutputStream(new File(path), true)))

    val printer: ZManaged[Has[Config], Throwable, PrintWriter] =
      ZManaged.accessManaged[Has[Config]](env => ZManaged.fromAutoCloseable(newPrinter(env.get.logPath)))

    def logger(prt: PrintWriter): URManaged[Has[Clock.Service] with Has[Counter.Service], Logger.Service] = {
      def println(str: String): Task[Unit] = ZIO(prt.println(str))

      val createLogger = for {
        cnt <- ZIO.service[Counter.Service]
        clk <- ZIO.service[Clock.Service]
      } yield {
        new Logger.Service {
          override def info(message: => String): UIO[Unit] =
            (for {
              n <- cnt.nextN
              t <- clk.currentDateTime
              _ <- println(f"$n%03d info $t: $message")
            } yield {}).ignore

          override def warning(message: => String): UIO[Unit] =
            (for {
              n <- cnt.nextN
              t <- clk.currentDateTime
              _ <- println(f"$n%03d warn $t: $message")
            } yield {}).ignore
        }
      }

      createLogger.toManaged(_.info("closing logger"))
    }

    ZLayer.fromManaged(printer >>= logger)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    //RLayer[Console with system.System with Clock, Logger]
    val providedLogger = (configLayer ++ counterLayer.fresh ++ ZLayer.identity[Clock with Console]) >>> loggerLayer

    //RLayer[Console with system.System, Logger with Counter with Has[Config]]
    val customs = providedLogger ++ counterLayer ++ configLayer

    //RIO[Console with Clock with Logger with Counter with Has[Config], Unit]
    val prgUsingConfig = ZIO.access[Has[Config]](_.get.nTimes) >>= Logic.prg

    prgUsingConfig.provideCustomLayer(customs).exitCode
  }

}
