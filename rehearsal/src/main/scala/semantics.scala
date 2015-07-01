package rehearsal

import rehearsal.ResourceModel.SshAuthorizedKey

/*
 * Give filesystem semantics to resources
 *
 * Expresses resources in terms of file system changes
 */
object ResourceToExpr {

  import FSSyntax.{Expr, Skip}
  import rehearsal.Implicits._
  import rehearsal.{ResourceModel => R}

  import java.nio.file.{Path, Files, Paths}

  import puppet.graph._
  import puppet.graph.Implicits._

  val pkgcache = PackageCache()

  def PackageDependencies(pkg: String): List[String] = {
    val cmd = s"""apt-rdepends --show=depends $pkg | grep -v '^ ' | grep -v $pkg"""
    val (sts, out, err) = Cmd.exec(cmd)
    /*  Toposort
     *  Among dependent packages of this package, apt-get will install the
     *  package with all its dependencies present first(in a reverse
     *  topological sort order)
     */
     out.lines.toList
  }

  val validBoolVals = List ((BoolV(true): Value, true),
                            (BoolV(false): Value, false),
                            (StringV("yes"): Value, true),
                            (StringV("no"): Value, false)).toMap

  def validVal[T](r: Resource, property: String,
                  options: Map[Value, T]): Option[T] = {
    r.getRawVal(property).map(options.get(_)).flatten
  }

  def validVal(r: Resource, property: String,
               options: List[String]): Option[String] = {
    val m = options.map((o) => (StringV(o): Value, o)).toMap
    validVal(r, property, m)
  }

  def File(r: Resource) = {

    val validEnsureVals = List("present", "absent", "file", "directory", "link")

    val path = r.get[String]("path") getOrElse r.name
    val source = r.get[String]("source")
    val content = r.get[String]("content")
    val ensure = validVal(r, "ensure", validEnsureVals) orElse {
      if(content.isDefined) Some("present") else None
    } orElse {
      if(source.isDefined) Some("file") else None
    }
    val force = validVal(r, "force", validBoolVals) getOrElse false
    val purge = validVal(r, "purge", validBoolVals) getOrElse false
    val target = r.get[String]("target")
    val owner = r.get[String]("owner")
    val provider = r.get[String]("provider")
    val mode = r.get[String]("mode")

    val props: Map[String, Option[String]] = Map(
      "path" -> Some(path),
      "content" -> content,
      "source" -> source,
      "ensure" -> ensure,
      "force" -> Some(force.toString))

    (props("ensure"), props("path"), props("content"), props("source"),
     r.get[Boolean]("force").getOrElse(false)) match {
       case (Some("present"), Some(p), Some(c), None, _) =>  R.File(p, c, false)
        case (Some("present"), Some(p), None, Some(c), _) => R.File(p, c, false)
        case (Some("present"), Some(p), None, None, _) => R.File(p, "", false)
        case (Some("absent"), Some(p), _, _, true) => R.AbsentPath(p, true)
        case (Some("absent"), Some(p), _, _, false) => R.AbsentPath(p, false)
        case (Some("file"), Some(p), Some(c), None, true) => R.File(p, c, true)
        case (Some("file"), Some(p), None, Some(c), true) => R.File(p, c, true)
        case (Some("file"), Some(p), None, None, true) => R.File(p, "", true)
        case (Some("file"), Some(p), Some(c), None, false) =>  R.EnsureFile(p, c)
        case (Some("file"), Some(p), None, Some(c), false) => R.EnsureFile(p, c)
        case (Some("file"), Some(p), None, None, false) => R.EnsureFile(p, "")
        case (Some("directory"), Some(p), _, _, _) => R.Directory(p)
       case (Some("link"), Some(p),_,  _, _) => R.File(p, r.get[String]("target").get, true)
       case _ => throw Unexpected(s"ensure attribute missing for file ${r.name}, attrs = ${r.attributes} ${props("ensure")}")
     }
  }

  def PuppetPackage(r: Resource) = {

    val validEnsureVals = List("present", "installed", "absent", "purged", "held", "latest")

    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse "installed"
    val provider = r.get[String]("provider")

    if(provider.isDefined && provider.get != "apt") {
      throw Unexpected(s"""package(${r.name}): "${provider.get}" provider not supported""")
    }

    ensure match {
      case "present" | "installed" | "latest" => R.Package(r.name, true)
      case "absent" | "purged" => R.Package(r.name, false)
      case "held" => throw NotImplemented("NYI package held") // TODO
      case _ => throw Unexpected(s"Invalid value for ensure: ${ensure}")
    }
  }

  def User(r: Resource) = {
    val validEnsureVals = List("present", "absent", "role")
    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse "present"
    val managehome = validVal(r, "managehome", validBoolVals) getOrElse false
    if (r.get[String]("provider").getOrElse("useradd") != "useradd") {
      throw NotImplemented(s"user(${r.name}): provider not supported")
    }
    (ensure, managehome) match {
      case ("present", true) => R.User(r.name, true, true)
      case ("present", false) => R.User(r.name, true, false)
      case ("absent", _) => R.User(r.name, false, managehome)
      case (_, _) => throw Unexpected(s"value for ensure: $ensure")
    }
  }

  def Group(r: Resource) = {
    val validEnsureVals = List("present", "absent")
    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse {
      throw Unexpected(s"group ${r.name} 'ensure' attribute missing")
    }
    val provider = r.get[String]("provider")
    if (provider.getOrElse("groupadd") != "groupadd") {
      throw NotImplemented(s"""group(${r.name}): "${provider.get}" provider not supported""")
    }
    ensure match {
      case "present" => R.Group(r.name, true)
      case "absent" => R.Group(r.name, false)
      case _ => throw Unexpected(s"ensure value is $ensure")
    }
  }
  
  def convert(r: Resource): ResourceModel.Res = r.typ.toLowerCase match {
    case "file" => File(r)
    case "package" => PuppetPackage(r)
    case "user" => User(r)
    case "group" => Group(r)
    case "ssh_authorized_key" => SshAuthorizedKey(r.get[String]("user").get, r.get[Boolean]("ensure").getOrElse(true),
      r.get[String]("name").get, r.get[String]("key").get)
    case "service" => R.Service(r.title)
//    case "Notify" => Skip
//    case "Exec" => {
//      println("WARNING: found an exec resource, but treating as Skip")
//      Skip
//    }
    case _ => throw NotImplemented("Resource type \"%s\" not supported yet".format(r.typ))
  } 

  def apply(r: Resource): Expr = convert(r).compile
    
}
