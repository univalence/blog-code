package io.univalence.kstreams.lib

import zio.{RIO, Task, UIO, ZIO}

/**
  * Wrap a mutable dependency into a ZIO context.
  *
  * @param a reference to the mutable dependency
  * @tparam A mutable dependency type
  */
abstract class WrapMutable[A](private val a: A) {

  final protected def executeTotal[B](f: A => B): UIO[B] = UIO(f(a))

  final protected def executeTotalM[R, E, B](
      f: A => ZIO[R, E, B]
  ): ZIO[R, E, B] = f(a)

  final protected def unsafeTotal[B](f: A => B): B = f(a)

  final def execute[B](f: A => B): Task[B] = Task(f(a))

  final def executeM[R, B](f: A => RIO[R, B]): RIO[R, B] = Task(f(a)).flatten

}
