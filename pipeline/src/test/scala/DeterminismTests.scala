import org.scalatest.FunSuite

import pipeline._
import puppet.graph._
import eval._
import puppet.Facter
import z3analysis.Z3Evaluator

class DeterminismTestSuite extends InlineTestSuite {

  def genericTestRunner(resourceGraph: ResourceGraph,
                        fileScriptGraph: FileScriptGraph): Unit = {
    val myBdd = bdd.Bdd[TestFileState]((x, y) => x < y)
    val pre = WeakestPreconditions.wpGraphBdd(myBdd)(fileScriptGraph, myBdd.bddTrue)
    println(WeakestPreconditions.bddToPred(myBdd)(pre))
    assert(Z3Evaluator.isDeterministic(myBdd)(pre, fileScriptGraph))
  }

  test("trivial program with non-deterministic error") {
    import scalax.collection.Graph
    import Implicits._
    val fileScriptGraph: FileScriptGraph = Graph(Mkdir("/foo"), Mkdir("/foo/bar"))
    val myBdd = bdd.Bdd[TestFileState]((x, y) => x < y)
    val pre = WeakestPreconditions.wpGraphBdd(myBdd)(fileScriptGraph, myBdd.bddTrue)
    println(WeakestPreconditions.bddToPred(myBdd)(pre))
    assert(Z3Evaluator.isDeterministic(myBdd)(pre, fileScriptGraph) == false)
  }

  test("trivial program with non-deterministic output") {
    import scalax.collection.Graph
    import Implicits._
    val fileScriptGraph: FileScriptGraph = Graph(
      If(TestFileState("/foo", IsDir), Mkdir("/bar"), Skip),
      Mkdir("/foo"))
    val myBdd = bdd.Bdd[TestFileState]((x, y) => x < y)
    val pre = WeakestPreconditions.wpGraphBdd(myBdd)(fileScriptGraph, myBdd.bddTrue)
    assert(Z3Evaluator.isDeterministic(myBdd)(pre, fileScriptGraph) == false)
  }


  test("Trivial, long program (performance test)") {
    import scalax.collection.Graph
    import Implicits._

    def genSeq(n: Int) : Expr = {
      import Implicits._
      if (n == 0) {
        Mkdir("/bar")
      }
      else {
        If(TestFileState("/foo", IsDir), Skip, Mkdir("/foo")) >> genSeq(n - 1)
      }
    }

    val fileScriptGraph: FileScriptGraph = Graph(
      genSeq(1000))
    val myBdd = bdd.Bdd[TestFileState]((x, y) => x < y)
    val pre = WeakestPreconditions.wpGraphBdd(myBdd)(fileScriptGraph, myBdd.bddTrue)
    assert(Z3Evaluator.isDeterministic(myBdd)(pre, fileScriptGraph) == true)
  }

  test("Trivial, long program with many files (performance test)") {
    import scalax.collection.Graph
    import Implicits._

    def genSeq(n: Int) : Expr = {
      import Implicits._
      if (n == 0) {
        Mkdir("/bar")
      }
      else {
        Mkdir(s"/$n") >> genSeq(n - 1)
      }
    }

    val fileScriptGraph: FileScriptGraph = Graph(genSeq(500))
    val myBdd = bdd.Bdd[TestFileState]((x, y) => x < y)
    val pre = WeakestPreconditions.wpGraphBdd(myBdd)(fileScriptGraph, myBdd.bddTrue)
    assert(Z3Evaluator.isDeterministic(myBdd)(pre, fileScriptGraph) == true)
  }
}
