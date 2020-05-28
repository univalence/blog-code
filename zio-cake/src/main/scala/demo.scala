package blog

import java.io.PrintWriter

import zio.{ ExitCode, Task, ZIO, ZManaged }

object Hello {
  import zio._
  import zio.console._
  import zio.clock._

  val prg: ZIO[Console with Clock, Exception, Unit] = for {
    _    <- putStr("Hello, what is your name ? > ")
    name <- getStrLn
    dt   <- currentDateTime
    _    <- putStrLn(s"Hello $name, it is $dt")
  } yield {}

  def main(args: Array[String]): Unit = {

    def mix(clock: Clock, console: Console): Clock with Console =
      clock ++ console

    val providedPrg_1: Task[Unit] = (for {
      clk <- clock.Clock.live.build
      cls <- console.Console.live.build
      res <- prg.provide(mix(clk, cls)).toManaged_
    } yield res).use(x => ZIO.unit)

    val providedPrg: Task[Unit] = prg.provideLayer(Clock.live ++ Console.live)

    {
      //val prg: ZIO[Console with Clock, Exception, Unit]
      val prgWithClock: ZIO[Console, Exception, Unit] =
        prg.provideSomeLayer[Console](Clock.live)
      val providedPrg: Task[Unit] = prgWithClock.provideLayer(Console.live)

      zio.Runtime.default.unsafeRun(providedPrg)

    }

    {

      val has: Has[Clock.Service] with Has[Console.Service] = Has(
        Clock.Service.live
      ) add Console.Service.live
      val providedPrg: IO[Exception, Unit] =
        prg.provide(has)
    }

    {
      val prgWithClock: ZIO[Console, Exception, Unit] = prg
        .provideSome[Console](x => x ++ Has(Clock.Service.live))

      val providedPrg: IO[Exception, Unit] =
        prgWithClock.provide(Has(Console.Service.live))
    }

    zio.Runtime.default.unsafeRun(providedPrg)
  }

  {
    val h1: Has[String] = Has("String")
    val h2: Has[Int]    = Has(1)

    val h3: Has[String] with Has[Int] = h1 ++ h2

  }

  {
    ZManaged.make(???)(???)

    val zlayer = ZLayer

  }

}

object TestPrinter extends zio.App {

  val printer: ZManaged[Any, Throwable, PrintWriter] =
    ZManaged.fromAutoCloseable(ZIO(new PrintWriter("log.txt")))

  val prg: ZManaged[Any, Throwable, Unit] =
    printer.mapEffect(obj => obj.println("Hello From ZIO!"))

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    prg.orDie.useNow.exitCode
}
