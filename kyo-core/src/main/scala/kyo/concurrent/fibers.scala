package kyo.concurrent

import kyo.concurrent.scheduler.IOPromise
import kyo.concurrent.scheduler.IOTask
import kyo.core._
import kyo.frames._
import kyo.ios._
import kyo.scopes._
import kyo.lists.Lists

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.LockSupport
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.concurrent.duration.Duration
import scala.util._
import scala.util.control.NonFatal

import scheduler._
import java.util.concurrent.ScheduledFuture
import java.io.Closeable

object fibers {

  private val timer = Executors.newScheduledThreadPool(1, ThreadFactory("kyo-fiber-sleep-timer"))

  opaque type Promise[T] <: Fiber[T] = IOPromise[T]
  opaque type Fiber[T]               = IOPromise[T]

  extension [T](p: Promise[T]) {

    inline def complete(v: => T > IOs): Boolean > IOs =
      IOs(p.complete(IOs(v)))
  }

  extension [T](f: Fiber[T]) {

    def isDone: Boolean > IOs =
      IOs(f.isDone)

    def join: T > Fibers =
      f > Fibers

    def joinTry: Try[T] > (Fibers | IOs) =
      IOs {
        val p = new IOPromise[Try[T]]
        p.interrupts(f)
        f.onComplete { t =>
          p.complete(Try(IOs.run(t)))
        }
        p > Fibers
      }

    inline def block: T > IOs =
      IOs(f.block())

    def interrupt: Boolean > IOs =
      IOs(f.interrupt())
  }

  final class Fibers private[fibers] extends Effect[Fiber] {

    def promise[T]: Promise[T] > IOs =
      IOs(IOPromise[T])

    def forkFiber[T](v: => T > (IOs | Scopes)): Fiber[T] > IOs =
      IOs(IOTask(IOs(v)))

    def fork[T](v: => T > (IOs | Scopes)): T > (IOs | Fibers) =
      forkFiber(v)(_.join)

    def fork[T1, T2](
        v1: => T1 > (IOs | Scopes),
        v2: => T2 > (IOs | Scopes)
    ): (T1, T2) > (IOs | Fibers) =
      collect(List(IOs(v1), IOs(v2)))(s => (s(0).asInstanceOf[T1], s(1).asInstanceOf[T2]))

    def fork[T1, T2, T3](
        v1: => T1 > (IOs | Scopes),
        v2: => T2 > (IOs | Scopes),
        v3: => T3 > (IOs | Scopes)
    ): (T1, T2, T3) > (IOs | Fibers) =
      collect(List(IOs(v1), IOs(v2), IOs(v3)))(s =>
        (s(0).asInstanceOf[T1], s(1).asInstanceOf[T2], s(2).asInstanceOf[T3])
      )

    def fork[T1, T2, T3, T4](
        v1: => T1 > (IOs | Scopes),
        v2: => T2 > (IOs | Scopes),
        v3: => T3 > (IOs | Scopes),
        v4: => T4 > (IOs | Scopes)
    ): (T1, T2, T3, T4) > (IOs | Fibers) =
      collect(List(IOs(v1), IOs(v2), IOs(v3), IOs(v4)))(s =>
        (s(0).asInstanceOf[T1], s(1).asInstanceOf[T2], s(2).asInstanceOf[T3], s(3).asInstanceOf[T4])
      )

    def race[T](
        v1: => T > (IOs | Scopes),
        v2: => T > (IOs | Scopes)
    ): T > (IOs | Fibers) =
      raceFiber(List(IOs(v1), IOs(v2)))(_.join)

    def race[T](
        v1: => T > (IOs | Scopes),
        v2: => T > (IOs | Scopes),
        v3: => T > (IOs | Scopes)
    ): T > (IOs | Fibers) =
      raceFiber(List(IOs(v1), IOs(v2), IOs(v2)))(_.join)

    def race[T](
        v1: => T > (IOs | Scopes),
        v2: => T > (IOs | Scopes),
        v3: => T > (IOs | Scopes),
        v4: => T > (IOs | Scopes)
    ): T > (IOs | Fibers) =
      raceFiber(List(IOs(v1), IOs(v2), IOs(v2), IOs(v4)))(_.join)

    def raceFiber[T](l: List[T > (IOs | Scopes)]): Fiber[T] > IOs =
      require(!l.isEmpty)
      IOs {
        val p = IOPromise[T]
        l.foreach { io =>
          val f = IOTask(io)
          p.interrupts(f)
          f.onComplete(p.complete(_))
        }
        p
      }

    def await[T](
        v1: => T > (IOs | Scopes)
    ): Unit > (IOs | Fibers) =
      fork(v1)(_ => ())

    def await[T](
        v1: => T > (IOs | Scopes),
        v2: => T > (IOs | Scopes)
    ): Unit > (IOs | Fibers) =
      awaitFiber(List(IOs(v1), IOs(v2)))(_.join)

    def await[T](
        v1: => T > (IOs | Scopes),
        v2: => T > (IOs | Scopes),
        v3: => T > (IOs | Scopes)
    ): Unit > (IOs | Fibers) =
      awaitFiber(List(IOs(v1), IOs(v2), IOs(v2)))(_.join)

    def await[T](
        v1: => T > (IOs | Scopes),
        v2: => T > (IOs | Scopes),
        v3: => T > (IOs | Scopes),
        v4: => T > (IOs | Scopes)
    ): Unit > (IOs | Fibers) =
      awaitFiber(List(IOs(v1), IOs(v2), IOs(v2), IOs(v4)))(_.join)

    def awaitFiber[T](l: List[T > (IOs | Scopes)]): Fiber[Unit] > IOs =
      IOs {
        val p       = IOPromise[Unit]
        val pending = AtomicInteger(l.size)
        var i       = 0
        val f: T > (IOs | Scopes) => Unit =
          r =>
            try {
              IOs.run(r)
              if (pending.decrementAndGet() == 0) {
                p.complete(())
              }
            } catch {
              case ex if (NonFatal(ex)) =>
                p.complete(IOs(throw ex))
            }
        l.foreach { io =>
          val fiber = IOTask(io)
          p.interrupts(fiber)
          fiber.onComplete(f)
          i += 1
        }
        p
      }

    def collect[T](l: List[T > (IOs | Scopes)]): Seq[T] > (IOs | Fibers) =
      collectFiber[T](l)(_.join)

    def collectFiber[T](l: List[T > (IOs | Scopes)]): Fiber[Seq[T]] > IOs =
      IOs {
        val p       = IOPromise[Seq[T]]
        val size    = l.size
        val results = (new Array[Any](size)).asInstanceOf[Array[T]]
        val pending = AtomicInteger(size)
        var i       = 0
        l.foreach { io =>
          val fiber = IOTask(io)
          p.interrupts(fiber)
          val j = i
          fiber.onComplete { r =>
            try {
              results(j) = IOs.run(r)
              if (pending.decrementAndGet() == 0) {
                p.complete(ArraySeq.unsafeWrapArray(results))
              }
            } catch {
              case ex if (NonFatal(ex)) =>
                p.complete(IOs(throw ex))
            }
          }
          i += 1
        }
        p
      }

    def sleep(d: Duration): Unit > (IOs | Fibers) =
      IOs {
        val p = new IOPromise[Unit] with Runnable with (ScheduledFuture[_] => Unit) {
          @volatile var timerTask: ScheduledFuture[_] = null
          def apply(f: ScheduledFuture[_]) =
            timerTask = f
          def run() =
            super.complete(())
            val t = timerTask
            if (t != null) {
              t.cancel(false)
              timerTask = null
            }
        }
        if (d.isFinite) {
          p(timer.schedule(p, d.toMillis, TimeUnit.MILLISECONDS))
        }
        p > Fibers
      }

    def block[T, S](v: T > (S | Fibers)): T > (S | IOs) =
      given ShallowHandler[Fiber, Fibers] =
        new ShallowHandler[Fiber, Fibers] {
          def pure[T](v: T) =
            val p = IOPromise[T]
            p.complete(v)
            p
          def apply[T, U, S](m: Fiber[T], f: T => U > (S | Fibers)) =
            f(m.block())
        }
      IOs((v < Fibers)(_.block()))
  }
  val Fibers = new Fibers

  inline given DeepHandler[Fiber, Fibers] =
    new DeepHandler[Fiber, Fibers] {
      def pure[T](v: T) =
        val p = IOPromise[T]
        p.complete(v)
        p
      def flatMap[T, U](fiber: Fiber[T], f: T => Fiber[U]): Fiber[U] =
        val r = IOPromise[U]
        r.interrupts(fiber)
        fiber.onComplete { v =>
          try f(IOs.run(v)).onComplete(r.complete(_))
          catch {
            case ex if (NonFatal(ex)) =>
              r.complete(IOs(throw ex))
          }
        }
        r
    }
}