package com.typelead

import java.lang.ProcessBuilder.Redirect
import java.lang.{ProcessBuilder => JProcessBuilder}

import sbt.Keys._
import sbt._

import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

object Etlas {

  private def getParam(name: String): Option[String] = {
    Option(System.getProperty(name)).map(_.toUpperCase)
  }

  private def defaultFilterLog(s: String): Boolean = {
    getParam("etlas.logger.output") match {
      case Some("TRUE") => true
      case _ => false
    }
  }

  private def etlas(args: Seq[String],
                    cwd: File,
                    log: Logger,
                    saveOutput: Boolean = false,
                    filterLog: String => Boolean = defaultFilterLog): Seq[String] = {

    val lineBuffer = new ArrayBuffer[String]

    val logger = new ProcessLogger {
      override def out(s: => String): Unit = {
        if (saveOutput) lineBuffer += s
        if (filterLog(s)) log.info(s)
      }
      override def err(s: => String): Unit = {
        if (saveOutput) lineBuffer += s
        log.error(s)
      }
      override def buffer[T](s: => T): T = s
    }

    val logCmd = getParam("etlas.logger.cmd.level") match {
      case Some("INFO") => log.info _
      case _ => log.debug _
    }
    logCmd(s"Running `etlas ${args.mkString(" ")} in '$cwd'`...")
    val exitCode = Process("etlas" +: args, cwd) ! logger

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

    if (saveOutput) lineBuffer else Nil

  }

  private def fork(args: Seq[String], cwd: File, log: sbt.Logger): Unit = {

    val logCmd = getParam("etlas.logger.cmd.level") match {
      case Some("INFO") => Logger(log).info _
      case _ => Logger(log).debug _
    }
    logCmd(s"Running `etlas ${args.mkString(" ")} in '$cwd'`...")

    val jpb = new JProcessBuilder(("etlas" +: args).toArray: _ *)
    jpb.directory(cwd)
    jpb.redirectInput(Redirect.INHERIT)
    val exitCode = Process(jpb).run(SbtUtils.terminalIO).exitValue()

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

  }

  // Commands

  private def withBuildDir(args: Seq[String], dist: File): Seq[String] = {
    args ++ Seq("--builddir", dist.getCanonicalPath)
  }

  def build(cwd: File, dist: File, log: Logger): Unit = {
    etlas(withBuildDir(Seq("build"), dist), cwd, log, filterLog = _ => true)
    ()
  }

  def buildArtifacts(cwd: File, dist: File, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    Cabal.getArtifacts(cwd, log, filter).foreach {
      artifact => etlas(withBuildDir(Seq("build", artifact.depsPackage), dist), cwd, log, filterLog = _ => true)
    }
  }

  def clean(cwd: File, dist: File, log: Logger): Unit = {
    etlas(withBuildDir(Seq("clean"), dist), cwd, log)
    ()
  }

  def deps(cwd: File, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Seq[String] = {
    Cabal.getArtifacts(cwd, log, filter).flatMap { artifact =>
      etlas(Seq("deps", artifact.depsPackage), cwd, log, saveOutput = true)
    }
  }

  def etaVersion(cwd: File, log: Logger): String = {
    etlas(Seq("exec", "eta", "--", "--numeric-version"), cwd, log, saveOutput = true).head
  }

  def etlasVersion(cwd: File, log: Logger): String = {
    etlas(Seq("--numeric-version"), cwd, log, saveOutput = true).head
  }

  def init(cwd: File,
           name: String,
           description: String,
           version: String,
           developers: Seq[Developer],
           homepage: Option[URL],
           src: File,
           log: Logger): Unit = {
    log.info("Initialize project...")
    etlas(Seq(
      "init",
      "--non-interactive",
      "--is-executable",
      s"--package-dir=${cwd.getCanonicalPath}",
      s"--package-name=$name-eta",
      s"--synopsis=$description",
      s"--version=$version",
      s"--source-dir=${IO.relativize(cwd, src).getOrElse(src.getCanonicalPath)}",
      "--language=Haskell2010"
    ) ++ developers.headOption.toList.flatMap(
      dev => Seq(s"--author=${dev.name}", s"--email=${dev.email}")
    ) ++ homepage.map(
      url => s"--homepage=$url"
    ), cwd, log)
  }

  def install(cwd: File, log: Logger): Unit = {
    log.info("Installing dependencies...")
    etlas(Seq("install", "--dependencies-only"), cwd, log)
  }

  def repl(cwd: File, dist: File, log: sbt.Logger): Try[Unit] = {
    def console0(): Unit = {
      log.info("Starting Eta interpreter...")
      fork(withBuildDir(Seq("repl"), dist), cwd, log)
    }
    Run.executeTrapExit(console0(), log).recover {
      case _: InterruptedException =>
        log.info("Eta REPL was interrupted.")
        ()
    }
  }

  def run(cwd: File, dist: File, log: Logger): Unit = {
    etlas(withBuildDir(Seq("run"), dist), cwd, log)
    ()
  }

  def runArtifacts(cwd: File, dist: File, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Unit = {
    Cabal.getArtifacts(cwd, log, Cabal.Artifact.and(Cabal.Artifact.executable, filter)).foreach { artifact =>
      etlas(withBuildDir(Seq("run", artifact.name), dist), cwd, log, filterLog = _ => true)
    }
  }

  def test(cwd: File, dist: File, log: Logger): Unit = {
    etlas(withBuildDir(Seq("test"), dist), cwd, log, filterLog = _ => true)
  }

  def testArtifacts(cwd: File, dist: File, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Unit = {
    Cabal.getArtifacts(cwd, log, Cabal.Artifact.and(Cabal.Artifact.testSuite, filter)).foreach { artifact =>
      etlas(withBuildDir(Seq("test", artifact.name), dist), cwd, log, filterLog = _ => true)
    }
  }

  // Resolve dependencies

  private def getArtifactsJars(cwd: File, dist: File, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Classpath = {
    Cabal.parseCabal(cwd, log).map { cabal =>
      val buildPath = dist / "build" / ("eta-" + Etlas.etaVersion(cwd, log)) / cabal.packageId
      Cabal.getArtifacts(cwd, log, filter).map {
        case Cabal.Library(_) =>
          buildPath / "build" / (cabal.packageId + "-inplace.jar")
        case Cabal.Executable(exe) =>
          buildPath / "x" / exe / "build" / exe / (exe + ".jar")
        case Cabal.TestSuite(suite) =>
          buildPath / "t" / suite / "build" / suite / (suite + ".jar")
      }.flatMap(jar => PathFinder(jar).classpath)
    }.getOrElse(Nil)
  }

  private def findAllDependencies(allLines: Seq[String], idx: Int): Seq[String] = {
    for {
      line <- allLines if line.startsWith("dependency,")
      parts = line.split(",") if parts.length > idx
    } yield parts(idx)
  }

  private def parseDeps(allLines: Seq[String]): Seq[String] = {
    findAllDependencies(allLines, 3)
  }

  private def findAllMavenDependencies(allLines: Seq[String]): Seq[String] = {
    for {
      line <- allLines if line.startsWith("maven-dependencies,")
    } yield line.dropWhile(_ != ',').tail
  }

  private def parseMavenDeps(allLines: Seq[String]): Seq[ModuleID] = {
    for {
      line <- findAllMavenDependencies(allLines) ++ findAllDependencies(allLines, 2)
      parts <- line.split(":").grouped(3) if parts.length == 3
      module = parts(0) % parts(1) % parts(2)
    } yield module
  }

  def getLibraryDependencies(cwd: File, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Seq[ModuleID] = {
    log.info("Checking Maven dependencies...")
    parseMavenDeps(Etlas.deps(cwd, log, filter))
  }

  def getFullClasspath(cwd: File, dist: File, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Classpath = {
    log.info("Retrieving Eta dependency jar paths...")

    val output = Etlas.deps(cwd, log, filter)
    val etaCp = parseDeps(output)
      .map(s => PathFinder(file(s)))
      .fold(PathFinder.empty)((s1, s2) => s1 +++ s2)

    val packageJars = Etlas.getArtifactsJars(cwd, dist, log, filter)

    packageJars.foreach { jar =>
      log.info("JAR: " + jar.data.getAbsolutePath)
    }

    etaCp.classpath ++ packageJars
  }

}
