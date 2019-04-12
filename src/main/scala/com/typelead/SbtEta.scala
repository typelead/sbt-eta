package com.typelead

import sbt.{Def, _}
import Keys._

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.sys.process.{Process, ProcessLogger}

object SbtEta extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val etaCompile = TaskKey[Unit]("eta-compile", "Build your Eta project.")
    val etaRun     = TaskKey[Unit]("eta-run", "Run your Eta project.")
    val etaTest    = TaskKey[Unit]("eta-test", "Run your Eta project's tests.")
    val etaClean   = TaskKey[Unit]("eta-clean", "Clean your Eta project.")

    val etaSource  = SettingKey[File]("eta-source", "Default Eta source directory.")
    val etaTarget  = SettingKey[File]("eta-target", "Location to store build artifacts.")
  }

  import autoImport._

  val baseEtaSettings: Seq[Def.Setting[_]] = Seq(
    etaTarget := target.value / "eta" / "dist",

    etaCompile in Compile := {
      val s   = streams.value
      val cwd = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("build", "--builddir", dist), cwd, Left(s), filterLog = _ => true)
      ()
    },

    etaCompile in Test := {
      val s   = streams.value
      val cwd = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      getArtifacts(cwd, Left(s), Artifact.testSuite).foreach {
        artifact => etlas(Seq("build", artifact.depsPackage, "--builddir", dist), cwd, Left(s), filterLog = _ => true)
      }
      ()
    },

    etaSource in Compile := (sourceDirectory in Compile).value / "eta",

    etaClean := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("clean", "--builddir", dist), cwd, Left(s))
      ()
    },

    etaRun := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("run", "--builddir", dist), cwd, Left(s))
      ()
    },

    etaTest := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      getArtifacts(cwd, Left(s), Artifact.testSuite).foreach {
        artifact => etlas(Seq("test", artifact.name, "--builddir", dist), cwd, Left(s), filterLog = _ => true)
      }
      ()
    },

    clean := {
      etaClean.value
      clean.value
    },

    libraryDependencies := {
      val s    = sLog.value
      val deps = libraryDependencies.value
      val cwd  = (etaSource in Compile).value

      s.info("[etlas] Installing dependencies...")
      etlas(Seq("install", "--dependencies-only"), cwd, Right(s))

      deps ++
        getLibraryDependencies(cwd, Right(s), Artifact.not(Artifact.testSuite)) ++
        getLibraryDependencies(cwd, Right(s), Artifact.testSuite).map(_ % Test)
    },

    unmanagedJars in Compile := {
      (libraryDependencies in Compile).value
      (etaCompile in Compile).value

      val s    = streams.value
      val cp   = (unmanagedJars in Compile).value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath

      cp ++ getFullClasspath(cwd, dist, Left(s), Artifact.not(Artifact.testSuite))
    },
    unmanagedJars in Test := {
      (libraryDependencies in Compile).value
      (etaCompile in Test).value

      val s    = streams.value
      val cp   = (unmanagedJars in Test).value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath

      cp ++ getFullClasspath(cwd, dist, Left(s), Artifact.testSuite)
    },

    compile in Test := {
      (etaCompile in Test).value
      (compile in Test).value
    },
    test in Test := {
      etaTest.value
      (test in Test).value
    },

    mainClass in (Compile, run) := {
      getMainClass((etaSource in Compile).value, (mainClass in (Compile, run)).value, Left(streams.value))
    },
    mainClass in (Compile, packageBin) := {
      getMainClass((etaSource in Compile).value, (mainClass in (Compile, packageBin)).value, Left(streams.value))
    },

    watchSources ++= ((etaSource in Compile).value ** "*").get,
  )

  override def projectSettings: Seq[Def.Setting[_]] = baseEtaSettings

  implicit class LoggerOps(val streams: Either[TaskStreams, Logger]) extends AnyVal {

    def debug(s: String): Unit = streams match {
      case Left(out)  => out.log.debug(s)
      case Right(out) => out.debug(s)
    }

    def info(s: String): Unit = streams match {
      case Left(out)  => out.log.info(s)
      case Right(out) => out.info(s)
    }

    def warn(s: String): Unit = streams match {
      case Left(out)  => out.log.warn(s)
      case Right(out) => out.warn(s)
    }

    def error(s: String): Unit = streams match {
      case Left(out)  => out.log.error(s)
      case Right(out) => out.error(s)
    }

  }

  def defaultFilterLog(s: String): Boolean = {
    getParam("etlas.logger.output") match {
      case Some("TRUE") => true
      case _ => false
    }
  }

  def etlas(
    args: Seq[String],
    cwd: File,
    streams: Either[TaskStreams, Logger],
    saveOutput: Boolean = false,
    filterLog: String => Boolean = defaultFilterLog
  ): Seq[String] = {
    val lineBuffer = new ArrayBuffer[String]

    val logger =
      new ProcessLogger {
        override def out(s: => String): Unit = {
          lineBuffer += s
          if (filterLog(s)) {
            streams.info("[etlas] " ++ s)
          }
        }
        override def err(s: => String): Unit = {
          lineBuffer += s
          streams.error("[etlas] " ++ s)
        }
        override def buffer[T](s: => T): T = s
      }

    val logCmd = getParam("etlas.logger.cmd.level") match {
      case Some("INFO") => streams.info(_)
      case _ => streams.debug(_)
    }
    logCmd(s"[etlas] Running `etlas ${args.mkString(" ")} in '$cwd'`...")
    val exitCode = Process("etlas" +: args, cwd) ! logger

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

    if (saveOutput) lineBuffer else Nil
  }

  def getLibraryDependencies(cwd: File,
                             streams: Either[TaskStreams, Logger],
                             filter: Artifact.Filter = Artifact.all): Seq[ModuleID] = {
    streams.info("[etlas] Checking Maven dependencies...")
    parseMavenDeps(resolveDeps(cwd, streams, filter))
  }

  def resolveDeps(cwd: File, streams: Either[TaskStreams, Logger], filter: Artifact.Filter = Artifact.all): Seq[String] = {
    getArtifacts(cwd, streams, filter).flatMap { artifact =>
      etlas(Seq("deps", artifact.depsPackage), cwd, streams, saveOutput = true)
    }
  }

  def findAllMavenDependencies(allLines: Seq[String]): Seq[String] = {
    for {
      line <- allLines if line.startsWith("maven-dependencies,")
    } yield line.dropWhile(_ != ',').tail
  }

  def findAllDependencies(allLines: Seq[String], idx: Int): Seq[String] = {
    for {
      line <- allLines if line.startsWith("dependency,")
      parts = line.split(",") if parts.length > idx
    } yield parts(idx)
  }

  def parseMavenDeps(allLines: Seq[String]): Seq[ModuleID] = {
    for {
      line <- findAllMavenDependencies(allLines) ++ findAllDependencies(allLines, 2)
      parts <- line.split(":").grouped(3) if parts.length == 3
      module = parts(0) % parts(1) % parts(2)
    } yield module
  }

  def parseDeps(allLines: Seq[String]): Seq[String] = {
    findAllDependencies(allLines, 3)
  }

  def getCabalFile(cwd: File): Option[String] = {
    cwd.listFiles
      .map(_.getName)
      .filter(_.matches(""".*\.cabal$"""))
      .headOption
  }

  def getProjectName(cwd: File, cabal: String): Option[String] = {
    Source.fromFile(cwd / cabal).getLines
      .filter(_.matches("""\s*name:\s*\S+\s*$"""))
      .toSeq
      .headOption
      .map(_.split(":")(1))
      .map(_.trim)
  }

  def getProjectVersion(cwd: File, cabal: String): Option[String] = {
    Source.fromFile(cwd / cabal).getLines
      .filter(_.matches("""\s*version:\s*\S+\s*$"""))
      .toSeq
      .headOption
      .map(_.split(":")(1))
      .map(_.trim)
  }

  sealed trait Artifact {
    def name: String
    def depsPackage: String
  }
  final case class Library(override val name: String) extends Artifact {
    override def depsPackage: String = "lib:" + name
  }
  final case class Executable(override val name: String) extends Artifact {
    override def depsPackage: String = "exe:" + name
  }
  final case class TestSuite(override val name: String) extends Artifact {
    override def depsPackage: String = "test:" + name
  }

  object Artifact {

    type Filter = Artifact => Boolean

    val all: Filter = _ => true
    val library: Filter = {
      case Library(_) => true
      case _ => false
    }
    val executable: Filter = {
      case Executable(_) => true
      case _ => false
    }
    val testSuite: Filter = {
      case TestSuite(_) => true
      case _ => false
    }

    def not(filter: Filter): Filter = a => !filter(a)

  }

  def getArtifacts(cwd: File, streams: Either[TaskStreams, Logger], filter: Artifact.Filter): Seq[Artifact] = {
    val LibraryPattern = """\s*library(\s*)$""".r
    val ExecutableWithName = """\s*executable\s*(\S+)\s*$""".r
    val ExecutableWithoutName = """\s*executable(\s*)$""".r
    val TestSuiteWithName = """\s*test-suite\s*(\S+)\s*$""".r
    val TestSuiteWithoutName = """\s*test-suite(\s*)$""".r
    getCabalFile(cwd) match {
      case Some(cabal) =>
        getProjectName(cwd, cabal) match {
          case Some(lib) =>
            Source.fromFile(cwd / cabal).getLines.collect {
              case LibraryPattern(_)        => Library(lib)
              case ExecutableWithName(exe)  => Executable(exe)
              case ExecutableWithoutName(_) => Executable(lib)
              case TestSuiteWithName(suite) => TestSuite(suite)
              case TestSuiteWithoutName(_)  => TestSuite(lib)
            }.filter(filter).toList.sortBy {
              case Library(_)    => 0
              case Executable(_) => 1
              case TestSuite(_)  => 2
            }
          case None =>
            streams.error("[etlas] No project name specified.")
            Nil
        }
      case None =>
        streams.error("[etlas] No cabal file found.")
        Nil
    }
  }

  def getArtifactsJars(cwd: File,
                       cabal: String,
                       dist: String,
                       streams: Either[TaskStreams, Logger],
                       filter: Artifact.Filter = Artifact.all): Classpath = {

    val etaVersion   = etlas(Seq("exec", "eta", "--", "--numeric-version"), cwd, streams, saveOutput = true).head
    val etlasVersion = etlas(Seq("--numeric-version"), cwd, streams, saveOutput = true).head

    (getProjectName(cwd, cabal), getProjectVersion(cwd, cabal)) match {
      case (Some(projectName), Some(projectVersion)) =>
        val packageId = projectName + "-" + projectVersion
        val buildPath = file(dist) / "build" / ("eta-" + etaVersion) / packageId
        getArtifacts(cwd, streams, filter).map {
          case Library(_) =>
            buildPath / "build" / (packageId + "-inplace.jar")
          case Executable(exe) =>
            buildPath / "x" / exe / "build" / exe / (exe + ".jar")
          case TestSuite(suite) =>
            buildPath / "t" / suite / "build" / suite / (suite + ".jar")
        }.flatMap(jar => PathFinder(jar).classpath)
      case _ =>
        Nil
    }

  }

  def getFullClasspath(cwd: File,
                       dist: String,
                       streams: Either[TaskStreams, Logger],
                       filter: Artifact.Filter = Artifact.all): Classpath = {
    streams.info("[etlas] Retrieving Eta dependency jar paths...")
    getCabalFile(cwd) match {
      case Some(cabal) =>
        val output = resolveDeps(cwd, streams, filter)
        val etaCp = parseDeps(output)
          .map(s => PathFinder(file(s)))
          .fold(PathFinder.empty)((s1, s2) => s1 +++ s2)

        val packageJars = getArtifactsJars(cwd, cabal, dist, streams, filter)

        packageJars.foreach { jar =>
          streams.info("[etlas] JAR: " + jar.data.getAbsolutePath)
        }

        etaCp.classpath ++ packageJars
      case None =>
        streams.error("[etlas] No cabal file found.")
        Nil
    }
  }

  def hasExecutable(cwd: File, streams: Either[TaskStreams, Logger]): Boolean = {
    getArtifacts(cwd, streams, Artifact.executable).nonEmpty
  }

  def getMainClass(cwd: File, defaultMainClass: Option[String], streams: Either[TaskStreams, Logger]): Option[String] = {
    if (hasExecutable(cwd, streams)) {
      Some("eta.main")
    } else {
      defaultMainClass
    }
  }

  def getParam(name: String): Option[String] = {
    Option(System.getProperty(name)).map(_.toUpperCase)
  }

}
