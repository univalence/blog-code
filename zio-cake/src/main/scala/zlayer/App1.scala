package zlayer.app1

import java.io.{ BufferedReader, File, FileOutputStream, FileReader, IOException, PrintWriter }

import zio.clock.Clock
import zio.console.Console
import zio.{ clock, console, ExitCode, Has, IO, Managed, RIO, Tagged, Task, UIO, URIO, ZIO, ZManaged }

object ImpureApp extends App {
  @throws[IOException]
  def readFirstLineFromFile(path: String): String =
    try {
      val br = new BufferedReader(new FileReader(path))
      try br.readLine
      finally if (br != null) br.close()
    }

  println(readFirstLineFromFile(".gitignore"))
}

object PureApp0 extends zio.App {
  def readFirstLineFromFile(path: String): Task[Option[String]] =
    Task(try {
      val br = new BufferedReader(new FileReader(path))
      try Option(br.readLine)
      finally if (br != null) br.close()
    })

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    val prg: ZIO[Console, Throwable, Unit] = for {
      firstline <- readFirstLineFromFile(".gitignore").someOrFailException
      _         <- zio.console.putStrLn(firstline)
    } yield {}

    prg.exitCode
  }
}

class IR[X <: AutoCloseable] private (x: X) {
  def execute[B](f: X => B): Task[B] = Task(f(x))
  val close: Task[Unit]              = execute(_.close())
}

object IR {
  def apply[X <: AutoCloseable](x: => X): Task[IR[X]] =
    Task(new IR(x))
}

object PureApp1 extends zio.App {

  def reader(path: String): Managed[Throwable, IR[BufferedReader]] = {
    val open = IR(new BufferedReader(new FileReader(path)))

    ZManaged.make(open)(brz => brz.close.ignore)
  }

  def readFirstLineFromFile(path: String): Task[Option[String]] =
    reader(path).use(ir => {
      val read = ir.execute(br => Option(br.readLine()))

      read
    })

  def readFirstNonEmptyLineFromFile(path: String): Task[Option[String]] =
    reader(path).use(ir => {
      val read = ir.execute(reader => Option(reader.readLine()))

      read.doUntil(_.forall(_.nonEmpty))
    })

  def readLines[R, A](path: String, f: Task[Option[String]] => RIO[R, A]): RIO[R, A] =
    reader(path).use(ir => {
      val readLine = ir.execute(br => Option(br.readLine()))
      f(readLine)
    })

  def readFirstLineFromFile1(path: String): Task[Option[String]] =
    readLines(path, x => x)

  def readFirstNonEmptyLineFromFile1(path: String): Task[Option[String]] =
    readLines(path, _.doUntil(_.forall(_.nonEmpty)))

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    val prg: ZIO[Console, Throwable, Unit] = for {
      firstline <- readFirstLineFromFile1(".gitignore").someOrFailException
      _         <- zio.console.putStrLn(firstline)
    } yield {}

    prg.exitCode
  }
}

trait LogService {
  type Void = UIO[Unit]
  def info(message: => String): Void
  def warning(message: => String): Void
}

object PureAppNoLog extends zio.App {

  import zio.console
  import zio.clock

  def prg(logger: LogService): RIO[Console with Clock, Unit] =
    for {
      _    <- logger.info("... starting ...")
      _    <- console.putStrLn("Hello User, what is your name ? ")
      name <- console.getStrLn
      _    <- logger.info(s"user = $name")
      dt   <- clock.currentDateTime
      _    <- console.putStrLn(s"Hello $name, it is $dt !")
    } yield {}

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {

    val NoLog = new LogService {
      override def info(message: => String): Void    = ZIO.unit
      override def warning(message: => String): Void = ZIO.unit
    }

    prg(NoLog).exitCode
  }
}

object envHas {
  def apply[A: Tagged]: URIO[Has[A], A] = ZIO.access[Has[A]](_.get)
}

object PureAppLog extends zio.App {

  import zio.console
  import zio.clock

  def prg(logger: LogService): RIO[Console with Clock, Unit] =
    for {
      _    <- logger.info("... starting ...")
      _    <- console.putStrLn("Hello User, what is your name ? ")
      name <- console.getStrLn
      _    <- logger.info(s"user = $name")
      dt   <- clock.currentDateTime
      _    <- console.putStrLn(s"Hello $name, it is $dt !")
    } yield {}

  def getLogger: ZManaged[Clock, Throwable, LogService] = {
    val getWritter: Managed[Throwable, IR[PrintWriter]] =
      ZManaged.make(IR(new PrintWriter(new FileOutputStream(new File("log.txt"), true))))(_.close.ignore)

    for {
      clock   <- ZIO.environment[Clock].toManaged_
      writter <- getWritter
    } yield {
      new LogService {
        private def log(level: String, message: String): UIO[Unit] =
          (clock.get.currentDateTime >>= (
            dt => writter.execute(_.println(s"[$level] $dt : $message"))
          )).ignore

        override def info(message: => String): Void    = log("info", message)
        override def warning(message: => String): Void = log("warning", message)
      }
    }

  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    //prg(NoLog).as(0).orDie
    getLogger.use(prg).exitCode

}

object PureAppLayer {

  def askUserName(logger: LogService): RIO[Console, String] =
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

  def startPrg(logger: LogService): UIO[Unit] = logger.info("... starting ...")

  def prg(logService: LogService): RIO[Console with Clock, Unit] =
    for {
      _    <- startPrg(logService)
      name <- askUserName(logService)
      _    <- greet(name)
    } yield {}
}
