package pipeline

import puppet.common.util._
import eval._
import eval.Implicits._

/*
 * Give filesystem semantics to resources
 *
 * Expresses resources in terms of file system changes
 */
private[pipeline] object ResourceToExpr {

  import java.nio.file.{Path, Files, Paths}

  import puppet.common.resource._
  import puppet.common.resource.Extractor._

  val pkgcache = {
    val benchmarksDir = Paths.get("benchmarks")
    if (!Files.isDirectory(benchmarksDir)) {
      Files.createDirectory(benchmarksDir)
    }
    val pkgcacheDir = benchmarksDir.resolve("pkgcache")
    if (!Files.isDirectory(pkgcacheDir)) {
      Files.createDirectory(pkgcacheDir)
    }
    new PackageCache(pkgcacheDir.toString)
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

  def File(r: Resource): Expr = {

    val validEnsureVals = List("present", "absent", "file", "directory", "link")

    val path = r.get[String]("path") getOrElse r.name
    val source = r.get[String]("source")
    val content = r.get[String]("content")
    val ensure = validVal(r, "ensure", validEnsureVals) orElse {
      if(content.isDefined) Some("present") else None
    }
    val force = validVal(r, "force", validBoolVals) getOrElse false
    val purge = validVal(r, "purge", validBoolVals) getOrElse false
    val target = r.get[String]("target")
    val owner = r.get[String]("owner")
    val provider = r.get[String]("provider")
    val mode = r.get[String]("mode")

    val p = path
//     val c = Content(content getOrElse "")
    val c = content getOrElse ""
//     val t = target getOrElse "/tmp/"

    val _ensure = if (ensure.isDefined) ensure
                  else if (source.isDefined) Some("file")
                  else None

    if(_ensure.isDefined && _ensure.get == "link") {
      throw new Exception(s"""file(${r.name}): "${_ensure.get}" ensure not supported""")
    }

    if(source.isDefined) {
      throw new Exception(s"""file(${r.name}): source attribute not supported""")
    }
    if(provider.isDefined && provider.get != "posix") {
      throw new Exception(s"""file(${r.name}): "${provider.get}" provider not supported""")
    }
    if(mode.isDefined) {
      throw new Exception(s"""file(${r.name}): "mode" attribute not supported""")
    }
    if(owner.isDefined) {
      throw new Exception(s"""file(${r.name}): "owner" attribute not supported""")
    }

    _ensure match {
      // Broken symlinks are ignored
      /* What if content is set
       *   - Depends on file type
       *     o For Links, content is ignored
       *     o For normal, content is applied
       *     o For directory, content is ignored
       */
      case Some("present") => If(TestFileState(p, IsFile),
                                 Block(Rm(p), CreateFile(p, c)), // true branch
                                 If(TestFileState(p, DoesNotExist),
                                    CreateFile(p, c),
                                    Skip))

      /*
       * Cases
       * If already absent then don't do anything
       *  Directory: if force is set then remove otherwise ignore
       *  File: remove if present
       *  Symlink: Remove link (but not the target)
       */
      case Some("absent") if force => If(TestFileState(p, IsDir),
                                         Rm(p),
                                         If(TestFileState(p, IsFile),
                                            Rm(p),
                                            Skip))

      case Some("absent") => If(TestFileState(p, IsFile),
                                Rm(p),
                                Skip)

      /* missing: Create a file with content if content present
       * directory: if force is set then remove directory createFile
       *            and set content if present else ignore
       * file: if content set then set content else ignore
       * link: removelink, createfile and set content if present
       */
      case Some("file") if force => Block(If(TestFileState(p, IsDir),
                                             Rm(p),
                                             If(TestFileState(p, IsFile),
                                                Rm(p),
                                                Skip)),
                                          CreateFile(p, c))

      case Some("file") => Block(If(TestFileState(p, IsFile),
                                    Rm(p),
                                    Skip),
                                 CreateFile(p, c))

      /* Missing: Create new directory
       * Directory: Ignore
       * File: remove file and create directory
       * link: remove link and create directory
       */
      case Some("directory") => If(TestFileState(p, IsDir), Skip,
                                   If(TestFileState(p, IsFile),
                                      Rm(p) >> Mkdir(p), Mkdir(p)))

      case _ => throw new Exception(s"ensure attribute missing for file ${r.name}")
    }
  }


  def PuppetPackage(r: Resource): Expr = {

    val validEnsureVals = List("present", "installed", "absent", "purged", "held", "latest")

    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse "installed"
    val provider = r.get[String]("provider")

    if(provider.isDefined && provider.get != "apt") {
      throw new Exception(s"""package(${r.name}): "${provider.get}" provider not supported""")
    }

    ensure match {

      case "present" | "installed" | "latest" => {

        val files = pkgcache.files(r.name) getOrElse
          (throw new Exception(s"Package not found: ${r.name}"))

        val allpaths = paths.allpaths(files)

        val dirs = (allpaths -- files)
        /*
         * XXX(if sorting becomes a bottleneck): Bucket sort below but unreadable!
        val mkdirs = (dirs - paths.root).groupBy(_.getNameCount)
                                        .mapValues(_.toSeq)
                                        .toSeq
                                        .sortBy(_._1)
                                        .unzip._2
                                        .flatten
                                        .map(d => If(TestFileState(d, DoesNotExist),
                                                     Mkdir(d), Skip)).toList
        */
        val mkdirs = (dirs - paths.root).toSeq.sortBy(_.getNameCount)
                                        .map(d => If(TestFileState(d, DoesNotExist),
                                                     Mkdir(d), Skip)).toList

//         val somecontent = Content("")
        val somecontent = ""
        val createfiles = files.map((f) => CreateFile(f, somecontent))

        val exprs = (mkdirs ++ createfiles)
        Block(exprs: _*)
      }

      case "absent" | "purged" => {

        val files = pkgcache.files(r.name) getOrElse
          (throw new Exception(s"Package not found: ${r.name}"))

        val exprs = files.map((f) => If(TestFileState(f, DoesNotExist),
                                        Skip, Rm(f))).toSeq
        Block(exprs: _*)
      }

      case "held"   => throw new Exception("NYI package held") // TODO
      case _ => throw new Exception(s"Invalid value for ensure: ${ensure}")
    }
  }

  def User(r: Resource): Expr = {

    val validEnsureVals = List("present", "absent", "role")

    import java.nio.file.{Files, LinkOption, Paths, Path}

    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse "present"
    val gid = r.get[String]("gid")
    val groups = r.get[Array[Value]]("groups") getOrElse {
      r.get[String]("groups").map((g) => Array((StringV(g): Value))) getOrElse {
        Array((UndefV: Value))
      }
    }

    val shell = r.get[String]("shell")
    val uid = r.get[String]("uid").map(_.toInt)
    // Directory must be created separately and is not checked for existence
    val home = r.get[String]("home")

    val comment = r.get[String]("comment")
    val expiry = r.get[String]("expiry")

    val allowdupe = validVal(r, "allowdupe", validBoolVals) getOrElse false
    val managehome = validVal(r, "managehome", validBoolVals) getOrElse false
    val system = validVal(r, "system", validBoolVals) getOrElse false
    val provider = r.get[String]("provider")

    def userExists(user: String): Boolean = {
      val (sts, _, _) = Cmd.exec(s"id -u $user")
      (sts == 0)
    }

    def gidExists(gid: String): Boolean = {
      val (sts, _, _) = Cmd.exec(s"getent group $gid")
      (sts == 0)
    }

    if(provider.isDefined && provider.get != "useradd") {
      throw new Exception(s"""user(${r.name}): "${provider.get}" provider not supported""")
    }

    val u = Paths.get(s"/etc/users/${r.name}")
    val usettings = Paths.get(s"/etc/users/${r.name}/settings")
//    val usettingscontent = Content("")
    val usettingscontent = ""
    val g = Paths.get(s"/etc/groups/${r.name}")
    val gsettings = Paths.get(s"/etc/groups/${r.name}/settings")
//    val gsettingscontent = Content("")
    val gsettingscontent = ""
    val h = Paths.get(home getOrElse s"/home/${r.name}")

    (ensure, managehome) match {

      case ("present", true) => If(TestFileState(u, DoesNotExist),
                                   Block(Mkdir(u),
                                         CreateFile(usettings, usettingscontent),
                                         If(TestFileState(g, DoesNotExist),
                                            Block(Mkdir(g), CreateFile(gsettings, gsettingscontent)),
                                            Skip),
                                         // TODO : Add to rest of groups
                                         If(TestFileState(h, DoesNotExist), Mkdir(h), Skip)),
                                   Skip)

      case ("present", false) => If(TestFileState(u, DoesNotExist),
                                    Block(Mkdir(u),
                                          CreateFile(usettings, usettingscontent),
                                          If(TestFileState(g, DoesNotExist),
                                             Block(Mkdir(g), CreateFile(gsettings, gsettingscontent)),
                                             Skip)
                                          // tODO: Add to rest of groups
                                          ),
                                    Skip)

      case ("absent", _) => If(!TestFileState(u, DoesNotExist),
                               Block(Rm(u),
                                     If(!TestFileState(g, DoesNotExist), Rm(g), Skip),
                                     If(!TestFileState(h, DoesNotExist), Rm(h), Skip)),
                               Skip)

      case (_, _) => throw new Exception(s"Unknown value present")
    }
  }


  def Group(r: Resource): Expr = {

    val validEnsureVals = List("present", "absent")

    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse
      (throw new Exception(s"Group ${r.name} 'ensure' attribute missing"))

    val provider = r.get[String]("provider")

    if(provider.isDefined && provider.get != "groupadd") {
      throw new Exception(s"""group(${r.name}): "${provider.get}" provider not supported""")
    }

    /* Semantics of Group resource
     *
     * A group name is a directory by the name of the group located at
     * location /etc/groups. Inside every directory there is a file called
     * settings that contains configuration data of every group
     *
     */
    val p = s"/etc/groups/${r.name}"
    val s = s"/etc/groups/${r.name}/settings"
//    val c = Content("")
    val c = ""

    ensure match {
      case "present" => If(TestFileState(p, DoesNotExist),
                           Mkdir(p) >> CreateFile(s, c),
                           CreateFile(s, c))

      case "absent" => If(!TestFileState(p, DoesNotExist), Rm(p), Skip)

      case _ => throw new Exception(s"Invalid ensure value: $ensure")
    }
  }


  def Exec(r: Resource): Expr = {

    val command = r.get[String]("command") getOrElse r.name
    val creates = r.get[String]("creates")
    val provider = r.get[String]("provider")

    if(provider.isDefined && provider.get == "windows") {
      throw new Exception(s"exec(${r.name}): windows command execution not supported")
    }

    if(creates.isDefined) {
      val p = creates.get
      /* TODO(nimish): Semantics of shell*/
      If(!TestFileState(p, DoesNotExist), Skip, Skip)
    }
    else { Skip /* TODO(nimish): Semantics of shell */ }
  }

  def Service(r: Resource): Expr = {

    val validEnsureVals: Map[Value, String] = Map(
      StringV("stopped") -> "stopped",
      BoolV(false) -> "stopped",
      StringV("running") -> "running",
      BoolV(true) -> "running",
      UndefV -> "undef"
    )
    val validBoolVal: Map[Value, Boolean] = Map(
      StringV("true") -> true,
      StringV("false") -> false
    )
    val validEnableVals = List("true", "false", "manual")
    val validHasRestartVals = validBoolVal
    val validHasStatusVals = validBoolVal

    val ensure = validVal(r, "ensure", validEnsureVals) getOrElse
      (throw new Exception(s"Service ${r.name} 'ensure' attribute missing"))
    val binary = r.get[String]("binary") getOrElse r.name
    // Decides whether a service should be enabled at boot time
    val enable = validVal(r, "enable", validEnableVals)
    val flags  = r.get[String]("flags") getOrElse ""
    val hasrestart = validVal(r, "hasrestart", validHasRestartVals) getOrElse false
    // if a service's init script has a functional status command,
    val hasstatus = validVal(r, "hasstatus", validHasStatusVals) getOrElse true
    val path = r.get[String]("path") getOrElse "/etc/init.d/"
    /* pattern to search for in process table, used for stopping services
     * that do not support init scripts
     *
     * Also used for determining service status on those service whose init
     * scripts do not include a status command
     */
    val pattern = r.get[String]("pattern") getOrElse binary
    // If not provided then service will be first stopped and then started
    val restart = r.get[String]("restart")
    val start = r.get[String]("start") getOrElse "start"
    val stop = r.get[String]("stop") getOrElse "stop"
    val status = r.get[String]("status")
    val provider = r.get[String]("provider")

    if (enable.isDefined && enable.get == "manual") {
      throw new Exception(s"""service(${r.name}): "manual" enable defined only for windows based system""")
    }

    if(provider.isDefined && provider.get != "upstart") {
      throw new Exception(s"""service(${r.name}: ${provider.get} unsupported on Ubuntu""")
    }

    val mode = ensure match {
      case "stopped" => "stop"
      case "running" => "start"
      case "undef" => "start"
      case _ => throw new Exception(s"service(${r.name}): Invalid value $ensure")
    }

    val command = s"${path}/${binary} ${flags} ${mode}"
    // ShellExec(command)
    Skip // TODO: Add ShellExec
  }


  def Notify(r: Resource): Expr = {

    val msg = r.get[String]("message") getOrElse r.name
    Skip
  }


  def apply(r: Resource): Expr = r.typ match {
    case "File" => File(r)
    case "Package" => PuppetPackage(r)
    case "User" => User(r)
    case "Notify" => Notify(r)
    case "Service" => Service(r)
    case "Group" => Group(r)
    case "Exec" => Exec(r)
    case _ => throw new Exception("Resource type \"%s\" not supported yet".format(r.typ))
  }
}
