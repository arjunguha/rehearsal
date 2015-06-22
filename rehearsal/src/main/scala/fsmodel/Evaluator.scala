package rehearsal.fsmodel
import rehearsal._
import java.nio.file.Path

object Eval {

  sealed trait FState
  case object FDir extends FState
  case class FFile(hash: Array[Byte]) extends FState
  type State = Map[Path, FState]

  def isDir(st: State, p: Path): Boolean = st.get(p) == Some(FDir)

  def doesNotExist(st: State, p: Path): Boolean = !st.contains(p)

  def isFile(st: State, p: Path): Boolean = st.get(p) match {
    case Some(FFile(_)) => true
    case _ => false
  }

  def evalPred(st: State, pred: Pred): Boolean = pred match {
    case True => true
    case False => false
    case ITE(a, b, c) => {
      if (evalPred(st, a)) {
        evalPred(st, b)
      }
      else {
        evalPred(st, c)
      }
    }
    case And(a, b) => evalPred(st, a) && evalPred(st, b)
    case Or(a, b) =>  evalPred(st, a) || evalPred(st, b)
    case Not(a) => !evalPred(st ,a)
    case TestFileState(p, IsFile) => isFile(st, p)
    case TestFileState(p, IsDir) => isDir(st, p)
    case TestFileState(p, DoesNotExist) => doesNotExist(st, p)
    case TestFileHash(_, _) => throw NotImplemented("nyi")
  }

  def eval(st: State, expr: Expr): Option[State] = expr match {
    case Error => None
    case Skip => Some(st)
    case Mkdir(p) => {
      if (doesNotExist(st, p) && isDir(st, p.getParent)) {
        Some(st + (p -> FDir))
      }
      else {
        None
      }
    }
    case CreateFile(p, h) => {
      if (isDir(st, p.getParent) && doesNotExist(st, p)) {
        Some(st + (p -> FFile(h)))
      }
      else {
        None
      }
    }
    case Cp(src, dst) => throw NotImplemented("not implemented")
    case Rm(p) => {
      if (isFile(st, p)) {
        Some(st - p)
      }
      else {
        None
      }
    }
    case Seq(e1, e2) => eval(st, e1).flatMap(st_ => eval(st_, e2))
    case If(pred, e1, e2) => {
      if (evalPred(st, pred)) {
        eval(st, e1)
      }
      else {
        eval(st, e2)
      }
    }
  }

}
