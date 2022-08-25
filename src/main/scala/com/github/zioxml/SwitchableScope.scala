package com.github.zioxml

import zio.ZIO
import zio.Ref
import zio.UIO
import zio.Scope

// TODO Investigate "ScopedRef" which may provide this functionality
class SwitchableScope[R,E,T] private (ref: Ref.Synchronized[T], acquire: => ZIO[R,E,T], release: T => ZIO[R,Nothing,Any]) {
  def get: UIO[T] = ref.get

  def switch: ZIO[R,E,T] = ref.updateAndGetZIO { current =>
    for {
      _ <- release(current)
      next <- acquire
    } yield next
  }

  private def close = for {
    current <- ref.get
    _ <- release(current)
  } yield ()
}

object SwitchableScope {
  def make[R,E,T](acquire: => ZIO[R,E,T])(release: T => ZIO[R,Nothing,Any]): ZIO[R with Scope, E, SwitchableScope[R,E,T]] = {
    ZIO.acquireRelease {
      for {
        initial <- acquire
        ref <- Ref.Synchronized.make(initial)
      } yield new SwitchableScope(ref, acquire, release)
    }(_.close)
  }
}
