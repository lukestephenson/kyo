package kyo

import ios._
import clocks._
import consoles._
import resources._
import tries._
import aborts._
import aspects._
import randoms._
import concurrent.fibers._
import concurrent.timers._
import scala.concurrent.duration.Duration
import kyo.KyoApp.Effects

abstract class KyoApp {

  final def main(args: Array[String]): Unit =
    IOs.run(KyoApp.runFiber(Duration.Inf)(run(args.toList)).map(_.block))

  def run(
      args: List[String]
  ): Unit > Effects

}

object KyoApp {

  type Effects =
    IOs with Fibers with Resources with Clocks with Consoles with Randoms with Timers with Aspects

  def runFiber[T](timeout: Duration)(v: T > Effects): Fiber[T] > IOs = {
    val v1
        : T > (IOs with Fibers with Resources with Clocks with Consoles with Timers with Aspects) =
      Randoms.run(v)
    val v2: T > (IOs with Fibers with Resources with Clocks with Timers with Aspects) =
      Consoles.run(v1)
    val v3: T > (IOs with Fibers with Resources with Timers with Aspects) = Clocks.run(v2)
    val v4: T > (IOs with Fibers with Timers with Aspects)                = Resources.run(v3)
    val v5: T > (IOs with Fibers with Timers)                             = Aspects.run(v4)
    val v6: T > (IOs with Fibers)                                         = Timers.run(v5)
    val v7: T > (IOs with Fibers with Timers) = Fibers.timeout(timeout)(v6)
    val v8: T > (IOs with Fibers)             = Timers.run(v6)
    IOs(Fibers.run(IOs.runLazy(v8)))
  }
}
