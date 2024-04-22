package kyo.scheduler.regulator

import kyo.scheduler.TestTimer
import org.scalatest.NonImplicitAssertions
import org.scalatest.freespec.AnyFreeSpec
import scala.concurrent.duration.*
import scala.util.Random

class AdmissionTest extends AnyFreeSpec with NonImplicitAssertions:

    "isn't affected if queuing delay is below low" in new Context:
        loadAvg = 0.7
        jitter = jitterLowerThreshold

        timer.advanceAndRun(regulateInterval * 2)
        assert(admission.percent() == 100)

    "reduces admission percent when queuing delay is high" in new Context:
        loadAvg = 0.9
        jitter = jitterUpperThreshold * 10

        timer.advanceAndRun(regulateInterval * 2)
        assert(admission.percent() == 97)

    "reject" - {

        "no key" in new Context:
            loadAvg = 0.9
            jitter = jitterUpperThreshold * 10

            timer.advanceAndRun(regulateInterval * 7)
            assert(admission.percent() == 41)

            val samples  = 10000
            val accepted = Seq.fill(samples)(()).count(_ => admission.reject())
            assert(Math.abs(accepted - samples * 41 / 100) < 200)

        "int key" in new Context:
            loadAvg = 0.9
            jitter = jitterUpperThreshold * 10

            timer.advanceAndRun(regulateInterval * 7)
            assert(admission.percent() == 41)

            val samples  = 10000
            val rejected = Seq.fill(samples)(Random.nextInt()).count(admission.reject)
            assert(Math.abs(rejected - samples * 41 / 100) < 200)

        "string key" in new Context:
            loadAvg = 0.9
            jitter = jitterUpperThreshold * 10

            timer.advanceAndRun(regulateInterval * 7)
            assert(admission.percent() == 41)

            val samples  = 10000
            val rejected = Seq.fill(samples)(Random.nextString(10)).count(admission.reject)
            assert(Math.abs(rejected - samples * 41 / 100) < 200)
    }

    trait Context:
        val timer           = TestTimer()
        var loadAvg: Double = 0.8
        var jitter          = 0L

        val collectWindow        = 10
        val collectInterval      = 10.millis
        val regulateInterval     = 100.millis
        val jitterUpperThreshold = 100
        val jitterLowerThreshold = 80
        val loadAvgTarget        = 0.8
        val stepExp              = 1.5

        val admission = new Admission(
            () => loadAvg,
            task =>
                timer.scheduleOnce(jitter.millis) {
                    task.run(0, null)
                    ()
                }
                ()
            ,
            () => timer.currentNanos / 1000000,
            timer,
            Config(
                collectWindow,
                collectInterval,
                regulateInterval,
                jitterUpperThreshold,
                jitterLowerThreshold,
                loadAvgTarget,
                stepExp
            )
        )
    end Context

end AdmissionTest
