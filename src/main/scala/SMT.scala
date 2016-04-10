package rehearsal

import smtlib.parser.CommandsResponses

case class SMTError(resp: smtlib.parser.CommandsResponses.FailureResponse)
  extends RuntimeException(resp.toString)

object SMT {

  import smtlib.parser.Terms._
  import smtlib.theories.Core

  private val names = collection.mutable.Map[String,Int]()

  val True = Core.True
  val False = Core.False
  val BoolSort = Core.BoolSort

  def Equals(x: Term, y: Term): Term = Core.Equals(x, y)

  def freshName(base: String = "x"): SSymbol = {
    names.get(base) match {
      case None => {
        names += (base -> 1)
        SSymbol(base + "0")
      }
      case Some(n) => {
        names += (base -> (n + 1))
        SSymbol(s"$base$n")
      }
    }
  }

  object Not {

    def apply(arg: Term): Term = arg match {
      case Core.Not(x) => x
      case _ => Core.Not(arg)
    }
  }

  object Implies {
    def apply(pred: Term, cons: Term) = Core.Implies(pred, cons)
  }


  def ite(cond: Term, tru: Term, fls: Term): Term = {
    if (tru == fls) {
      tru
    }
    else {
      Core.ITE(cond, tru, fls)
    }
  }

  private case object FoundAnnihilator extends RuntimeException("")

  object Or {

    // flatten(terms) == None means that terms is equivalent to true
    private def flatten(term: Term): Seq[Term] = term match {
      case Or(terms) =>  terms.flatMap(t => flatten(t))
      case Core.True() => throw FoundAnnihilator
      case Core.False() => Seq()
      case _ => Seq(term)
    }

    def apply(terms: Term*): Term = {
      try {
        terms.flatMap(flatten) match {
          case Seq() => False()
          case Seq(t) => t
          case xs => FunctionApplication(QualifiedIdentifier(Identifier(SSymbol("or"))), xs)
        }
      }
      catch {
        case FoundAnnihilator => True()
      }
    }

    def unapply(term: Term): Option[Seq[Term]] = term match {
      case FunctionApplication(QualifiedIdentifier(Identifier(SSymbol("or"), Seq()), None), terms) => Some(terms)
      case _ => None
    }

  }

  object And {

    private def flatten(term: Term): Seq[Term] = term match {
      case And(terms) => terms.flatMap(flatten)
      case False() => throw FoundAnnihilator
      case True() => Seq()
      case _ => Seq(term)
    }

    def apply(terms: Term*): Term = {
      try {
        terms.flatMap(flatten) match {
          case Seq() => True()
          case Seq(x) => x
          case xs => FunctionApplication(QualifiedIdentifier(Identifier(SSymbol("and"))), xs)
        }
      }
      catch {
        case FoundAnnihilator => False()
      }
    }

    def unapply(term: Term): Option[Seq[Term]] = term match {
      case FunctionApplication(QualifiedIdentifier(Identifier(SSymbol("and"), Seq()), None), terms) => Some(terms)
      case _ => None
    }

  }

  object Implicits {

    import scala.language.implicitConversions

    implicit class RichTerm(term: Term) {

      def &&(other: Term): Term = And(term, other)
      def ||(other: Term): Term = Or(term, other)

    }

    implicit def stringToQualID(str: String): QualifiedIdentifier = {
      QualifiedIdentifier(Identifier(SSymbol(str)))
    }

    implicit def symbolToQualID(s: SSymbol): QualifiedIdentifier = {
      QualifiedIdentifier(Identifier(s))
    }

  }


}

class SMT() extends com.typesafe.scalalogging.LazyLogging {

  import java.nio.file._
  import smtlib.parser.Commands._
  import smtlib.parser.CommandsResponses._
  import smtlib.parser.Terms._
  import smtlib.interpreters.Z3Interpreter


  private val interpreter = Z3Interpreter.buildDefault

  logger.info("Started Z3")

  def free(): Unit = {
    logger.info("Stopped Z3")
    interpreter.free()
  }

  def interrupt(): Unit = {
    interpreter.interrupt()
  }

  def pushPop[A](thunk: => A): A = {
    try {
      eval(Push(1))
      thunk
    }
    finally {
      eval(Pop(1))
    }
  }

  def getModel(): List[SExpr] = eval(GetModel()).asInstanceOf[GetModelResponseSuccess].model

  def checkSat(): Boolean = {
    logger.info("Starting a (check-sat) query")
    time(eval(CheckSat()).asInstanceOf[CheckSatStatus].status) match {
      case (SatStatus, t) => {
        logger.info(s"Solver produced sat in $t ms")
        true
      }
      case (UnsatStatus, t) => {
        logger.info(s"Solver produced unsat in $t ms")
        false
      }
      case (UnknownStatus, t) => throw Unexpected(s"CheckSat produced unknown in $t milliseconds")
    }
  }

  def getValue(terms: Seq[Term]): Seq[(Term, Term)] = terms match {
    case Seq() => Seq()
    case _ => eval(GetValue(terms.head, terms.tail)).asInstanceOf[GetValueResponseSuccess].valuationPairs
  }

  def eval(command: Command) :  CommandResponse = {
    val resp = interpreter.eval(command)
    resp match {
      case Error(msg) => {
        logger.error(s"Error from SMT solver: $msg")
        throw SMTError(Error(msg))
      }
      case Unsupported => {
        logger.error("Unsupported from SMT solver")
        throw SMTError(Unsupported)
      }
      case (resp: CommandResponse) => {
        logger.debug(resp.toString)
        resp
      }
      case _ => throw Unexpected("not a CommandResponse")
    }
  }

}
