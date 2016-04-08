package rehearsal

private object PuppetEval {

  import PuppetSyntax._
  import scalax.collection.Graph
  import scalax.collection.Graph._
  import scalax.collection.GraphPredef._
  import scalax.collection.GraphEdge._
  import scalax.collection.edge.Implicits._
  import Implicits._
  import scala.util.parsing.combinator._

  object StringInterpolator {

    def interpolate(st: State, env: Env, str: String): Expr = {
      val strs = str.split("""\$""")
      EStr(strs(0) + strs.toList.drop(1).map(helper(st, env)).mkString(""))
    }

    def helper(st: State, env: Env)(str: String): String = {
      val tokens = str.split("""\s+""").toList
      checkBraces(st, env, tokens(0)) + tokens.drop(1).mkString("")
    }

    def checkBraces(st: State, env: Env, str: String): String = {
      str.indexOf("{") match {
        case 0 => {
          val ix = str.indexOf("}")
          val expr = str.substring(1, ix)
          val rest = str.substring(ix+1, str.length)
          evaluate(st, env, expr) + rest
        }
        case _ => evaluate(st, env, str)
      }
    }

    // TODO(arjun): We should really parse in the parser
    def evaluate(st: State, env: Env, str: String): String = {
      val strPrime = if(str.charAt(0) != '$') "$" + str else str
      PuppetParser.parseExpr(strPrime) match {
        case Some(expr) => evalExpr(st, env, expr) match {
          case EStr(s) => s
          case _ => throw EvalError(s"None string expression evaluated during string interpolation: $expr")
        }
        case m => throw EvalError(s"Could not parse interpolated expression: $m")
      }
    }

  }

  val primTypes =  Set("file", "package", "user", "group", "service",
                       "ssh_authorized_key", "augeas", "notify")

  case class VClass(
    name: String,
    env: Env,
    args: Map[String, Option[Expr]],
    body: Manifest)

  case class VDefinedType(
    name: String,
    env: Env,
    args: Map[String, Option[Expr]],
    body: Manifest)

  case class Env(scope: Map[String, Expr], resourceDefaults: Map[String, Map[String, Expr]], enclosing: Option[Env]) {

    // NOTE(arjun): Do not change this. Strictly speaking, this is not an
    // error. But, silently returning undef will make us miss other bugs in
    // our implementation. If a test manifest actually uses this feature,
    // modify it so that the undeclared variable is set to Undef.
    def getOrError(x: String): Expr = {
      scope.getOrElse(x, enclosing match {
        case None => throw EvalError(s"undefined identifier $x")
        case Some(env) => env.getOrError(x)
      })
    }

    def newScope(): Env = Env(Map(), Map(), Some(this))

    def default(x: String, v: Expr) = scope.get(x) match {
      case None => Env(scope + (x -> v), resourceDefaults, enclosing)
      case Some(_) => this
    }

    def +(xv: (String, Expr)): Env = {
      val (x, v) = xv
      if (scope.contains(x)) {
        throw EvalError(s"identifier already set: $x")
      }
      else {
        Env(scope + (x -> v), resourceDefaults, enclosing)
      }
    }

    def forceSet(x: String, v: Expr) = new Env(scope + (x -> v), resourceDefaults, enclosing)

    def ++(other: Map[String, Expr]): Env = {
      Env(scope ++ other, resourceDefaults, enclosing)
    }

    def getDefaults(typ: String): Map[String, Expr] = resourceDefaults.getOrElse(typ, Map())

    def updateDefaults(typ: String, map: Map[String, Expr]): Env = {
     if (resourceDefaults.contains(typ) == false) {
       Env(scope, resourceDefaults + (typ -> map), enclosing)
     }
      else {
       val oldMap = resourceDefaults(typ)
       val alreadySet = oldMap.keySet
       val newlySet = map.keySet
       val overlap = alreadySet.intersect(newlySet)
       if (overlap.isEmpty) {
         val resourceDefaults1 = resourceDefaults + (typ -> (oldMap ++ map))
         Env(scope, resourceDefaults1, enclosing)
       }
       else {
         throw EvalError(s"already have default attributes for $typ ($overlap)")
       }

     }
    }

  }

  object Env {

    val empty = Env(Map(), Map(), None)

  }

  case class State(resources: Map[FSGraph.Key, ResourceVal],
                   deps: Graph[FSGraph.Key, DiEdge],
                   env: Env,
                   definedTypes: Map[String, VDefinedType],
                   classes: Map[String, VClass],
                   classGraph: Map[String, Graph[FSGraph.Key, DiEdge]],
                   stages: Map[String, Set[FSGraph.Key]],
                   aliases: Map[FSGraph.Key, FSGraph.Key])

  def evalAttrs(st: State, env: Env, attrs: Seq[Attribute]): Map[String, Expr] = {
    // TODO(arjun): duplicates are probably an error
    attrs.map({
      case Attribute(kExpr, v) => evalExpr(st, env, kExpr) match {
        case EStr(k) => k -> evalExpr(st, env, v)
        case _ => throw EvalError("attribute key should be a string")
      }
    }).toMap
  }

  def evalTitles(st: State, env: Env, titleExpr: Expr): Seq[String] = {
    evalExpr(st, env, titleExpr) match {
      case EStr(title) => Seq(title)
      case EArray(titles) => titles.map(x => evalTitle(st, env, x))
      case v => throw EvalError(s"title should be a string or an array, but got $v{titleExpr.pos}")
    }
  }

  def evalTitle(st: State, env: Env, titleExpr: Expr): String = {
    evalExpr(st, env, titleExpr) match {
      case EStr(title) => title
      case v => throw EvalError(s"title should be a string, but got $v ${titleExpr.pos}")
    }
  }

  def resourceRefs(e: Expr): Seq[Node] = e match {
    case EResourceRef(typ, EStr(title)) => Seq(Node(typ, title))
    case EArray(seq) => seq.map(resourceRefs).flatten
    case _ => throw EvalError(s"expected resource reference, got $e ${e.pos}")
  }

  def extractRelationships(s: FSGraph.Key,
                           attrs: Map[String, Expr]): Graph[FSGraph.Key, DiEdge] = {

    def get(key: String) = resourceRefs(attrs.getOrElse(key, EArray(Seq())))
    val before = get("before").map(r => DiEdge(s, r))
    val require = get("require").map(r => DiEdge(r, s))
    val notify = get("notify").map(r => DiEdge(s, r))
    val subscribe = get("subscribe").map(r => DiEdge(r, s))
    Graph.from(edges = Seq(before, require, notify, subscribe).flatten)
  }

  val metaparameters = Seq("before", "require", "notify", "subscribe", "alias")

  // Produces a new state and a list of resource titles
  // TODO(arjun): Handle "require" and "before" dependencies.
  def evalResource(st: State, resource: Resource): (State, List[FSGraph.Key]) = {
    resource match {
      case ResourceRef(typ, titleExpr, Seq()) => {
        val node = Node(typ.toLowerCase, evalTitle(st, st.env, titleExpr))
        (st.copy(deps = st.deps + node), List(node))
      }
      case ResourceDecl(typ, lst) => {
        val (vals, relationships, aliases) = lst.map({ case (titleExpr, attrs) =>
          val attrVals = st.env.getDefaults(typ) ++ evalAttrs(st, st.env, attrs)
          val resources = evalTitles(st, st.env, titleExpr).map(title => ResourceVal(typ, title, attrVals -- metaparameters))
          val relationships: Seq[Graph[FSGraph.Key, DiEdge]] = resources.map(r => extractRelationships(r.node, attrVals))
          val aliases = attrVals.get("alias") match {
            case Some(v) => {
              val alias = v.value[String].getOrElse(throw EvalError("alias must be a string"))
              resources.map(r => Map[FSGraph.Key, FSGraph.Key](Node(typ, alias) -> r.node)).reduce(_ ++ _)
            }
            case None => Map[FSGraph.Key, FSGraph.Key]()
          }
          (resources, relationships, aliases)
        }).unzip3
        val aliasMap = aliases.reduce(_ ++ _)
        val newNodes = vals.map(_.map(_.node)).flatten.toSet
        //println(s"Creating nodes $nodes")
        val redefinedResources = st.resources.keySet.intersect(newNodes)
        if (redefinedResources.isEmpty == false) {
          throw EvalError(s"${redefinedResources.head} is already defined")
        }
        else {
          val newResources: Map[FSGraph.Key, ResourceVal] = vals.map(rs => rs.map(r => r.node -> r).toMap).reduce(_ ++ _)
          (st.copy(resources = st.resources ++ newResources,
                   deps = st.deps ++
                     Graph[FSGraph.Key, DiEdge](aliasMap.keys.toSeq: _*) ++
                     Graph.from(newNodes, edges = Set()) ++
                     relationships.flatten.reduce(_ union _),
                   stages = newResources.foldRight(st.stages)(updateStage),
                   aliases = st.aliases ++ aliasMap),
           newNodes.toList)
        }
      }
    }
  }

  def evalResDefaults(st: State): State = {
    val ress: Seq[ResourceVal] = st.resources.map(_._2).toSeq
    val defaultRess = ress.filter(r => r.title == "")
    val newRess = defaultRess.map(r => evalResDefault(r.attrs, ress.filter(s => s.typ == r.typ))).flatten
    val newResMap = newRess.map(r => (Node(r.typ, r.title), r)).toMap

    //TODO: (rolivia) This is not right
    st.copy(resources = (newResMap ++ st.resources).filter(_._2.title != ""),
            deps = st.deps -- st.deps.nodes.filter(_.value.asInstanceOf[Node].title == ""))
  }

  def evalResDefaults2(st: State): State = {
    // val defaultRess = st.resources.filter(_._1.title == "")
    // defaultRess.map(r => evalResDefault(r._2.attrs,
                            // st.resources.filter(_._1.typ == r._1.typ).map(_._2).toSeq))
    val resources = st.resources.groupBy(_._1.asInstanceOf[Node].typ)
    resources.map(r => {

      })
    //defaultsT = map of default attributes for type T
    //resValsT = list of ResourceVals of type T
    // evalResDefault(defaultsT, resValsT)
    st
  }

  def evalResDefault(attrs: Map[String, Expr], ress: Seq[ResourceVal]): Seq[ResourceVal] = {
    ress match {
      case Seq() => Seq()
      case r :: t => {
        val newAttrs: Map[String, Expr] = attrs.filter(a => !r.attrs.contains(a._1))
        val newRes = ResourceVal(r.typ, r.title, r.attrs ++ newAttrs)
        newRes +: evalResDefault(attrs, t)
      }
    }
  }

  def evalEdges(st: State, lst: Seq[Resource]): (State, List[FSGraph.Key]) = lst match {
    case Seq() => (st, Nil)
    case r :: rest => {
      val (st1, titlesHd) = evalResource(st, r)
      val (st2, titlesTl) = evalEdges(st1, rest)
      val newDeps = for (x <- titlesHd; y <- titlesTl) yield { DiEdge(x, y) }
      (st2.copy(deps = st2.deps ++ newDeps), titlesHd)
    }
  }

  def matchCase(st: State, env: Env, v: Expr, aCase: Case): Boolean = aCase match {
    case CaseDefault(_) => true
    case CaseExpr(e, _) => evalExpr(st, env, e) == v
  }

  def evalManifest(st: State, manifest: Manifest): State = manifest match {
    case MEmpty => st
    case MSeq(m1, m2) => evalManifest(evalManifest(st, m1), m2)
    case MResources(lst) => evalEdges(st, lst)._1
    case MDefine(f, args, m) => {
      if (st.definedTypes.contains(f)) {
        throw EvalError(s"$f is already defined as a type")
      }
      else {
        // TODO(arjun): ensure no duplicate identifiers
        val vt = VDefinedType(f, st.env, args.map(a => a.id -> a.default).toMap,
                              m)
        st.copy(definedTypes = st.definedTypes + (f -> vt))
      }
    }
    case MClass(f, args, _, m) => {
      if (st.classes.contains(f)) {
        throw EvalError(s"$f is already defined as a class")
      }
      else {
        // TODO(arjun): ensure no duplicate identifiers
        val vc = VClass(f, st.env,
                        args.map(a => a.id -> a.default).toMap,
                        m)
        st.copy(classes = st.classes + (f -> vc))
      }
    }
    case MSet(x, e) => st.copy(env = st.env + (x -> evalExpr(st, st.env, e)))
    case MIte(e, m1, m2) => evalBool(st, st.env, e) match {
      case true => evalManifest(st, m1)
      case false => evalManifest(st, m2)
    }
    case MInclude(titleExprs) => titleExprs.foldRight(st)(evalTitle)

    case MApp("fail", Seq(str)) => evalExpr(st, st.env, str) match {
      // TODO(arjun): Different type of error
      case EStr(str) => throw EvalError(s"user-defined failure: $str")
      case v => throw EvalError(s"expected string argument, got $v ${manifest.pos}")
    }
    case MApp(f, _) => ???
    case MCase(e, cases) => {
      val v = evalExpr(st, st.env, e)
      cases.find(c => matchCase(st, st.env, v, c)) match {
        case None => st
        case Some(CaseDefault(m)) => evalManifest(st, m)
        case Some(CaseExpr(_, m)) => evalManifest(st, m)
      }
    }
    case MResourceDefault(typ, attrs) => {
      val map = evalAttrs(st, st.env, attrs)
      st.copy(env =  st.env.updateDefaults(typ.toLowerCase, map))
    }
  }

  def evalTitle(titleExpr: Expr, st: State): State = {
    val title = evalTitle(st, st.env, titleExpr)
    // TODO(arjun): Dependencies? Does include make the outer class depend on this class?
    val res = ResourceVal("class", title, Map())
    val node = Node("class", title)
    st.copy(resources = st.resources + (node -> res), deps = st.deps + node)
  }

  // Helps implement conditionals. "Truthy" values are mapped to Scala's true
  // and "falsy" values are mapped to Scala's false.
  def evalBool(st: State, env: Env, expr: Expr): Boolean = evalExpr(st, env, expr) match {
    case EBool(true) => true
    case EBool(false) => false
    case EStr(_) => true
    case EUndef => false
    case v => throw EvalError(s"predicate evaluated to $v")
  }

  def evalExpr(st: State, env: Env, expr: Expr): Expr = expr match {
    case EUndef => EUndef
    case ENum(n) => ENum(n)
    case EStr(str) => StringInterpolator.interpolate(st, env, str)
    case EBool(b) => EBool(b)
    case ERegex(r) => ERegex(r)
    case EResourceRef(typ, title) => EResourceRef(typ.toLowerCase, evalExpr(st, env, title))
    case EEq(e1, e2) => EBool(evalExpr(st, env, e1) == evalExpr(st, env, e2))
    case ELT(e1, e2) => (evalExpr(st, env, e1), evalExpr(st, env, e2)) match {
      case (ENum(n1), ENum(n2)) => EBool(n1 < n2)
      case _ => throw EvalError(s"expected args to LT to evaluate to Nums")
    }

    case ENot(e) => EBool(!evalBool(st, env, e))
    case EAnd(e1, e2) => EBool(evalBool(st, env, e1) && evalBool(st, env, e2))
    case EOr(e1, e2) => EBool(evalBool(st, env, e1) || evalBool(st, env, e2))
    case EVar(x) => env.getOrError(x)
    case EArray(es) => EArray(es.map(e => evalExpr(st, env, e)))
    case EApp("template", Seq(e)) => evalExpr(st, env, e) match {
      // TODO(arjun): This is bogus. It is supposed to use filename as a
      // template string. The file contents have patterns that refer to variables
      // in the environment.
      case EStr(filename) => EStr(filename)
      case _ => throw EvalError("template function expects a string argument")
    }
    case EApp("defined", Seq(rref)) => evalExpr(st, st.env, rref) match {
      case EResourceRef(typ, EStr(title)) => EBool(st.resources.contains(Node(typ, title)))
      case v => throw EvalError(s"expected resource reference as argument to defined")
    }
    case EApp(f, args) => ???
    case ECond(e1, e2, e3) => evalBool(st, env, e1) match {
      case true => evalExpr(st, env, e2)
      case false => evalExpr(st, env, e3)
    }
    case EMatch(e1, e2) => (evalExpr(st, env, e1), evalExpr(st, env, e2)) match {
      case (EStr(s), ERegex(r)) => {
        val pat = r.r
        s match {
          case pat(_) => EBool(true)
          case _ => EBool(false)
        }
      }
      case _ => throw EvalError(s"expected match to find a string on the LHS and a regex on the RHS")
    }
    case _ => ???
  }

  //Filters will not remove all unnecessary edges that are contained in inner graph
  def splice[A](outer: Graph[A, DiEdge], node: A,
                inner: Graph[A, DiEdge]): Graph[A, DiEdge] = {
    val innerNode = outer.get(node)
    val removeable = inner.nodes.filter(outer.nodes.contains(_))
    val newInner = removeable.foldRight(inner)({ case (n, g) => g - n })
    val toEdges = (for (from <- innerNode.diPredecessors;
                        to <- newInner.nodes.filter(_.inDegree == 0))
                   yield (DiEdge(from.value, to.value)))
    val fromEdges = (for (from <- newInner.nodes.filter(_.outDegree == 0);
                          to <- innerNode.diSuccessors)
                     yield (DiEdge(from.value, to.value)))
    val g = outer ++ inner ++ fromEdges ++ toEdges - innerNode
    g
  }

  def mergeNodes[A](g: Graph[A, DiEdge], src1: A, src2: A, dst: A): Graph[A, DiEdge] = {
    val es1 = for (x <- g.get(src1).diPredecessors) yield (DiEdge(x.value, dst))
    val es2 = for (x <- g.get(src2).diPredecessors) yield (DiEdge(x.value, dst))
    val es3 = for (x <- g.get(src1).diSuccessors) yield (DiEdge(dst, x.value))
    val es4 = for (x <- g.get(src2).diSuccessors) yield (DiEdge(dst, x.value))
    g ++ es1 ++ es2 ++ es3 ++ es4 + dst - src1 - src2
  }

  def evalArgs(st: State, formals: Map[String, Option[Expr]], actuals: Map[String, Expr], env: Env): Map[String, Expr] = {
    val unexpected = actuals.keySet -- actuals.keySet
    if (unexpected.isEmpty == false) {
      throw EvalError(s"unexpected arguments: ${unexpected}")
    }
    formals.toList.map {
      case ("title", None) => ("title", env.getOrError("title"))
      case (x, None) => actuals.get(x) match {
        case Some(v) => (x, v)
        case None => throw EvalError(s"expected argument $x")
      }
      case (x, Some(default)) => actuals.get(x) match {
        case Some(v) => (x, v)
        // Notice that default expressions are evaluated in the lexical scope
        // of the type definition, but with $title bound dynamically.
        case None => (x, evalExpr(st, env, default))
      }
    }.toMap
  }

  def instantiateClass(st: State, k: FSGraph.Key): State = {
    val node = k.asInstanceOf[Node]
    require(node.typ == "class")
    val title = node.title
    st.classGraph.get(title) match {
      // Class has already been instantiated, splice it in where necesarry
      case Some(deps) =>
          st.copy(deps = splice(st.deps, st.deps.get(Node("class", title)), deps))
      case None => st.classes.get(title) match {
        case Some(VClass(_, env, formals, m)) => {
          val ResourceVal(_, _, actuals) = st.resources(node)
          val env1 = env.forceSet("title", EStr(node.title)).forceSet("name", EStr(node.title))
          val evaluated = evalArgs(st, formals, actuals, env1)
          val env2 = (env.newScope ++ evaluated).default("title", EStr(node.title)).default("name", EStr(node.title))
          val st1 = evalManifest(st.copy(deps = Graph.empty, env = env2), m)
          st1.copy(
            resources = st1.resources - node,
            deps = splice(st.deps, st.deps.get(Node("class", title)), st1.deps),
            classGraph = st1.classGraph + (title -> st1.deps)
          )
        }
        case None => throw EvalError(s"class not found: $title ${st.classes.keys}")
      }
    }
  }

  def instantiateType(st: State, k: FSGraph.Key): State = {
    val node = k.asInstanceOf[Node]
    val VDefinedType(_, env, formals, m) = st.definedTypes(node.typ)
    val ResourceVal(_, _, actuals) = st.resources(node)
    val unexpected = actuals.keySet -- actuals.keySet
    if (unexpected.isEmpty == false) {
      throw EvalError(s"unexpected arguments: ${unexpected}")
    }
    val env1 = env.forceSet("title", EStr(node.title)).forceSet("name", EStr(node.title))
    val evaluated = evalArgs(st, formals, actuals, env1)
    val env2 = (env.newScope ++ evaluated).default("title", EStr(node.title)).default("name", EStr(node.title))
    val st1 = evalManifest(st.copy(deps = Graph.empty, env = env2), m)
    st1.copy(deps = splice(st.deps, node, st1.deps), resources = st1.resources - node)
  }


  def evalLoop(st: State): State = {
    val st1 = st
    // Select newly instantiated classes and splice them into the graph
    val instClasses = st1.deps.nodes.filter(_.value.asInstanceOf[Node].typ == "class").map(_.value)
    val st2 = instClasses.foldLeft(st1)(instantiateClass)
    // Select newly instantiated defined types and splice them into the graph
    val newInstances = st2.deps.nodes
      .filter(node => st2.definedTypes.contains(node.value.asInstanceOf[Node].typ))
      .map(_.value)
    val st3 = newInstances.foldLeft(st2)(instantiateType)
    if (newInstances.isEmpty && instClasses.isEmpty) {
      st3 // st1 == st2 == st3
    }
    else {
      evalLoop(st3)
    }
  }

  val emptyState = State(
    resources = Map(),
    deps = Graph.empty,
    env = Env.empty + ("title" -> EStr("main")) + ("name" -> EStr("main")),
    definedTypes = Map(),
    classes = Map(),
    classGraph = Map(),
    stages = Map(),
    aliases = Map())

  def updateStage(res: (FSGraph.Key, ResourceVal), stages: Map[String, Set[FSGraph.Key]]): Map[String, Set[FSGraph.Key]] = res match {
    case (node, ResourceVal(_, _, attrMap)) =>
      attrMap.get("stage") match {
        case Some(EStr(stage)) => addStage(stage, node, stages)
        case Some(stage) => throw EvalError(s"Stage evaluated to non-string. $stage")
        case None => addStage("main", node, stages)
      }
  }

  def addStage(stage: String, node: FSGraph.Key, stages: Map[String, Set[FSGraph.Key]]): Map[String, Set[FSGraph.Key]] =
    stages.get(stage) match {
      case None => stages + (stage -> Set(node))
      case Some(set) => stages + (stage -> (set + node))
    }

  def expandStage(k: FSGraph.Key, st: State): State = {
    val stage = k.asInstanceOf[Node]
    require(stage.typ == "stage")
    val stageNodes = st.stages.getOrElse(stage.title, throw Unexpected(s"Stage should be in map. $stage")).filter(x => st.deps.contains(x))
    val dependencies = st.deps.get(stage).incoming.map(x => st.stages.getOrElse(x.head.value.asInstanceOf[Node].title, throw Unexpected(s"Stage should be in map. $stage"))).flatten.filter(x => st.deps.contains(x))
    val st2 =
      stageNodes.foldRight(st)((n, st1) =>
        st1.copy(
          deps = st1.deps ++ dependencies.map(x => DiEdge(x, n))
        )
      )
    st2.copy( deps = st2.deps - stage,
              resources = st.resources - stage)
  }

  def stageExpansion(st: State): State = {
    val instStages = st.deps.nodes.filter(_.value.asInstanceOf[Node].typ == "stage").map(_.value)
    instStages.foldRight(st)(expandStage)
  }

  def eliminateAliases(st: State): State = {
    val deps = st.aliases.toSeq.foldRight(st.deps) { case ((alias, target), g) => mergeNodes(g, alias, target, target) }
    st.copy(deps = deps, aliases = Map())
  }

  def eliminateAnchors(st: State): State = {
    val anchors: Set[st.deps.NodeT] = st.deps.nodes.toSet.filter(_.value.asInstanceOf[Node].typ == "anchor")
    val edges = anchors.map(anchor => anchor.diPredecessors.foldRight[Set[DiEdge[FSGraph.Key]]](Set()) {
      case (pred, acc) => acc ++ anchor.diSuccessors.map(succ => DiEdge(pred.value, succ.value))
    }).flatten
    val graph: Graph[FSGraph.Key, DiEdge]  = st.deps ++ edges
    st.copy(resources = st.resources -- anchors.map(_.value), deps = graph -- anchors)
  }

  def eval(manifest: Manifest): EvaluatedManifest = {
    val st = eliminateAnchors(eliminateAliases(stageExpansion(evalLoop(evalManifest(emptyState, manifest)))))
    if (st.deps.isCyclic) {
      throw EvalError("Cycle in the resource graph")
    }
    EvaluatedManifest(st.resources, st.deps)
  }
}
