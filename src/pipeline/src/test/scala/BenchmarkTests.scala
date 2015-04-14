import pipeline._
import eval._

import org.scalatest.FunSuite

class BenchmarkTests extends FunSuite {

  private val facterEnv = Facter.run() getOrElse
    (throw new Exception("Facter environment required"))

  for ((name, b) <- BenchmarkLoader.benchmarks) {

    test(s"benchmark: $name") {
      val expr = pipeline.resourceGraphToExpr(b.toGraph(facterEnv).head._2)
      val finalStates = Ample.finalStates(Ubuntu.fs, expr)
      assert(1 == finalStates.size)
    }
  }
}
