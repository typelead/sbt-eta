package com.typelead

import sbt._
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
    val etaClean   = TaskKey[Unit]("eta-clean", "Clean your Eta project.")

    val etaSource  = SettingKey[File]("eta-source", "Default Eta source directory.")
    val etaTarget  = SettingKey[File]("eta-target", "Location to store build artifacts.")
  }

  import autoImport._

  val baseEtaSettings = Seq(
    etaTarget := target.value / "eta" / "dist",

    etaCompile in Compile := {
      val s   = streams.value
      val cwd = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("build", "--builddir", dist), cwd, Left(s))
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

      s.info("[etlas] Checking Maven dependencies...")

      getCabalFile(cwd) match {
        case Some(cabal) => resolveDeps(cabal, cwd, Right(s)) match {
          case Some(output) =>
            deps ++ parseMavenDeps(output)
          case None =>
            s.error("[etlas] No project name specified.")
            deps
        }
        case None =>
          s.error("[etlas] No cabal file found.")
          deps
      }
    },

    unmanagedJars in Compile := {
      (libraryDependencies in Compile).value
      (etaCompile in Compile).value

      val s    = streams.value
      val cp   = (unmanagedJars in Compile).value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath

      s.log.info("[etlas] Retrieving Eta dependency jar paths...")

      getCabalFile(cwd) match {
        case Some(cabal) => resolveDeps(cabal, cwd, Left(s)) match {
          case Some(output) =>
            val etaCp = parseDeps(output)
              .map(s => PathFinder(file(s)))
              .fold(PathFinder.empty)((s1, s2) => s1 +++ s2)

            val etaVersion   = etlas(Seq("exec", "eta", "--", "--numeric-version"), cwd, Left(s), saveOutput = true).head
            val etlasVersion = etlas(Seq("--numeric-version"), cwd, Left(s), saveOutput = true).head

            val packageJars = getArtifactsJars(cwd, cabal, dist, etaVersion)

            packageJars.foreach { jar =>
              s.log.info("[etlas] JAR: " + jar.data.getAbsolutePath)
            }

            cp ++ etaCp.classpath ++ packageJars
          case _ =>
            s.log.error("[etlas] No project name specified.")
            cp
        }
        case None =>
          s.log.error("[etlas] No cabal file found.")
          cp
      }
    },
    mainClass in (Compile, run) := {
      getMainClass((etaSource in Compile).value, (mainClass in (Compile, run)).value, streams.value)
    },
    mainClass in (Compile, packageBin) := {
      getMainClass((etaSource in Compile).value, (mainClass in (Compile, packageBin)).value, streams.value)
    },
    watchSources ++= ((etaSource in Compile).value ** "*").get
  )

  override def projectSettings = baseEtaSettings

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

    val logDebug = streams match {
      case Left(out)  => (s: String) => out.log.debug(s)
      case Right(out) => (s: String) => out.debug(s)
    }

    val logInfo = streams match {
      case Left(out)  => (s: String) => out.log.info(s)
      case Right(out) => (s: String) => out.info(s)
    }

    val logError = streams match {
      case Left(out)  => (s: String) => out.log.error(s)
      case Right(out) => (s: String) => out.error(s)
    }

    val logger =
      new ProcessLogger {
        override def out(s: => String): Unit = {
          lineBuffer += s
          if (filterLog(s)) {
            logInfo("[etlas] " ++ s)
          }
        }
        override def err(s: => String): Unit = {
          lineBuffer += s
          logError("[etlas] " ++ s)
        }
        override def buffer[T](s: => T): T = s
      }

    val logCmd = getParam("etlas.logger.cmd.level") match {
      case Some("INFO") => logInfo
      case _ => logDebug
    }
    logCmd(s"[etlas] Running `etlas ${args.mkString(" ")} in '$cwd'`...")
    val exitCode = Process("etlas" +: args, cwd) ! logger

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

    if (saveOutput) lineBuffer else Nil
  }

  def resolveDeps(cabal: String, cwd: File, streams: Either[TaskStreams, Logger]): Option[Seq[String]] = {
    for {
      artifacts <- Some(getArtifacts(cwd, cabal)) if artifacts.nonEmpty
    } yield artifacts.flatMap { artifact =>
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
    def depsPackage: String
  }
  final case class Library(name: String) extends Artifact {
    override def depsPackage: String = "lib:" + name
  }
  final case class Executable(name: String) extends Artifact {
    override def depsPackage: String = "exe:" + name
  }

  def getArtifacts(cwd: File, cabal: String): Seq[Artifact] = {
    val ExecutableWithName = """\s*executable\s*(\S+)\s*$""".r
    val ExecutableWithoutName = """\s*executable(\s*)$""".r
    val LibraryPattern = """\s*library(\s*)$""".r
    getProjectName(cwd, cabal).map { name =>
      Source.fromFile(cwd / cabal).getLines.collect {
        case LibraryPattern(_)        => Library(name)
        case ExecutableWithName(exe)  => Executable(exe)
        case ExecutableWithoutName(_) => Executable(name)
      }.toList.sortBy {
        case Library(_)    => 0
        case Executable(_) => 1
      }
    }.getOrElse(Nil)
  }

  def getArtifactsJars(cwd: File, cabal: String, dist: String, etaVersion: String): Classpath = {
    (getProjectName(cwd, cabal), getProjectVersion(cwd, cabal)) match {
      case (Some(projectName), Some(projectVersion)) =>
        val packageId = projectName + "-" + projectVersion
        val buildPath = file(dist) / "build" / ("eta-" + etaVersion) / packageId
        getArtifacts(cwd, cabal).map {
          case Executable(exeName) =>
            buildPath / "x" / exeName / "build" / exeName / (exeName + ".jar")
          case Library(_) =>
            buildPath / "build" / (packageId + "-inplace.jar")
        }.flatMap(jar => PathFinder(jar).classpath)
      case _ =>
        Nil
    }
  }

  def hasExecutable(cwd: File, cabal: String): Boolean = {
    getArtifacts(cwd, cabal).exists {
      case Executable(_) => true
      case Library(_) => false
    }
  }

  def getMainClass(cwd: File, defaultMainClass: Option[String], s: TaskStreams): Option[String] = {
    getCabalFile(cwd) match {
      case None =>
        s.log.error("[etlas] No cabal file found.")
        defaultMainClass
      case Some(cabal) if hasExecutable(cwd, cabal) =>
        Some("eta.main")
      case _ =>
        defaultMainClass
    }
  }

  def getParam(name: String): Option[String] = {
    Option(System.getProperty(name)).map(_.toUpperCase)
  }

}
