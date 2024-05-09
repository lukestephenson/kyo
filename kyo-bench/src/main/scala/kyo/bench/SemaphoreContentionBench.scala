package kyo.bench

import org.openjdk.jmh.annotations.*

class SemaphoreContentionBench extends Bench.ForkOnly(()):

    val permits   = 10
    val parallism = 100
    val depth     = 1000

    def catsBench() =
        import cats.effect.IO
        import cats.effect.std.Semaphore
        import cats.effect.std.CountDownLatch

        def repeat[A](n: Int)(io: IO[A]): IO[A] =
            if n <= 1 then io
            else io.flatMap(_ => repeat(n - 1)(io))

        def loop(sem: Semaphore[IO], cdl: CountDownLatch[IO], i: Int = 0): IO[Unit] =
            if i >= depth then
                cdl.release
            else
                sem.acquire.flatMap(_ => sem.release)
                    .flatMap(_ => loop(sem, cdl, i + 1))

        for
            sem <- Semaphore[IO](permits)
            cdl <- CountDownLatch[IO](parallism)
            _   <- repeat(parallism)(loop(sem, cdl).start)
            _   <- cdl.await
        yield {}
        end for
    end catsBench

    override def kyoBenchFiber() =
        import kyo.*

        def repeat[A](n: Int)(io: A < IOs): A < IOs =
            if n <= 1 then io
            else io.flatMap(_ => repeat(n - 1)(io))

        def loop(sem: Meter, cdl: Latch, i: Int = 0): Unit < Fibers =
            if i >= depth then
                cdl.release
            else
                sem.run(()).flatMap(_ => loop(sem, cdl, i + 1))

        for
            sem <- Meters.initSemaphore(permits)
            cdl <- Latches.init(parallism)
            _   <- repeat(parallism)(Fibers.init(loop(sem, cdl)))
            _   <- cdl.await
        yield {}
        end for
    end kyoBenchFiber

    def zioBench() =
        import zio.*
        import zio.concurrent.*

        def repeat[R, E, A](n: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
            if n <= 1 then zio
            else zio.flatMap(_ => repeat(n - 1)(zio))

        def loop(sem: Semaphore, cdl: CountdownLatch, i: Int = 0): Task[Unit] =
            if i >= depth then
                cdl.countDown
            else
                sem.withPermit(ZIO.succeed(()))
                    .flatMap(_ => loop(sem, cdl, i + 1))

        for
            sem <- Semaphore.make(permits)
            cdl <- CountdownLatch.make(parallism)
            _   <- repeat(parallism)(loop(sem, cdl).forkDaemon)
            _   <- cdl.await
        yield {}
        end for
    end zioBench

    @Benchmark
    def forkOx() =
        import java.util.concurrent.*
        import ox.*
        scoped {
            val sem = new Semaphore(permits, true)
            val cdl = new CountDownLatch(parallism)
            for _ <- 0 until parallism do
                fork {
                    for _ <- 0 to depth do
                        sem.acquire()
                        sem.release()
                    cdl.countDown()
                }
            end for
            cdl.await()
        }
    end forkOx
end SemaphoreContentionBench
