package eval

import Implicits._

private[eval] object Helpers {

  // True if there are no concurrent expressions nested within this expression.
  def isSequential(expr: Expr): Boolean = expr match {
    case Error => true
    case Skip => true
    case If(_, p, q) => p.isSequential && q.isSequential
    case Seq(p, q) => p.isSequential && q.isSequential
    case Alt(p, q) => p.isSequential && q.isSequential
    case Atomic(p) => p.isSequential
    case Concur(_, _) => false
    case Mkdir(_) => true
    case CreateFile(_, _) => true
    case Rm(_) => true
    case Cp(_, _) => true
  }

  // Gives all predicates size 0
  def size(expr: Expr): Int = expr match {
    case Error => 1
    case Skip => 1
    case If(_, p, q) => 1 + p.size + q.size
    case Seq(p, q) => 1 + p.size + q.size
    case Alt(p, q) => 1 + p.size + q.size
    case Atomic(p) => 1 + p.size
    case Concur(p, q) => 1 + p.size + q.size
    case Mkdir(_) => 1
    case CreateFile(_, _) => 1
    case Rm(_) => 1
    case Cp(_, _) => 1
  }

  // Converts predicates to negation normal form
  def nnf(pred: Pred): Pred = pred match {
    case Not(And(a, b)) => Or(nnf(Not(a)), nnf(Not(b)))
    case Not(Or(a, b)) => And(nnf(Not(a)), nnf(Not(b)))
    case Not(Not(a)) => nnf(a)
    case And(a, b) => And(nnf(a), nnf(b))
    case Or(a, b) => Or(nnf(a), nnf(b))
    case Not(a) => Not(nnf(a))
    case _ => pred
  }

  // Converts predicates from negation normal form to conjunctive normal form
  def cnfFromNnf(pred: Pred): Pred = pred match {
    case Or(a, And(b, c)) => cnfFromNnf(And(Or(a, b), Or(a, c)))
    case Or(And(b, c), a) => cnfFromNnf(And(Or(b, a), Or(c, a)))
    case And(a, b) => And(cnfFromNnf(a), cnfFromNnf(b))
    case Or(a, b) => Or(cnfFromNnf(a), cnfFromNnf(b))
    case Not(a) => Not(cnfFromNnf(a))
    case _ => pred
  }

  // Converts predicates to conjunctive normal form
  def cnf(pred: Pred): Pred = cnfFromNnf(nnf(pred))

  // Replaces a with b in pred.
  def replace(pred: Pred, a: Pred, b: Pred): Pred = pred match {
    case x if (a == x) => b
    case Or(x, y) => Or(replace(x, a, b), replace(y, a, b))
    case And(x, y) => And(replace(x, a, b), replace(y, a, b))
    case Not(x) => Not(replace(x, a, b))
    case _ => pred
  }

  // Calculates the weakest-precondition for an expression yielding the desired postcondition.
  def wp(expr: Expr, post: Pred): Pred = expr match {
    case Error => False
    case Skip => post
    case If(a, p, q) => (!a || wp(p, post)) && (a || wp(q, post))
    case Seq(p, q) => wp(p, wp(q, post))
    case Alt(p, q) => wp(p, post) && wp(q, post)
    case Atomic(p) => wp(p, post)
    case Concur(p, q) => wp(p, post) && wp(q, post)
    case Mkdir(f) => post.replace(TestFileState(f, IsDir), True)
      .replace(TestFileState(f, DoesNotExist), False).replace(TestFileState(f, IsFile), False) &&
      (TestFileState(f, DoesNotExist) && TestFileState(f.getParent(), IsDir))
    case CreateFile(f, _) => post.replace(TestFileState(f, IsDir), False)
      .replace(TestFileState(f, DoesNotExist), False).replace(TestFileState(f, IsFile), True) &&
      (TestFileState(f, DoesNotExist) && TestFileState(f.getParent(), IsDir))
    case Rm(f) => post.replace(TestFileState(f, IsDir), False)
      .replace(TestFileState(f, DoesNotExist), True).replace(TestFileState(f, IsFile), False) &&
      TestFileState(f, IsFile)
    case Cp(f, g) => post.replace(TestFileState(g, DoesNotExist), False)
      .replace(TestFileState(g, IsFile), TestFileState(f, IsFile))
      .replace(TestFileState(g, IsDir), TestFileState(f, IsDir)) &&
      (TestFileState(g, DoesNotExist) && !TestFileState(f, DoesNotExist))
    case _ => False
  }
}
