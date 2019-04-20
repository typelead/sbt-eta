package com.typelead

import java.lang.ProcessBuilder.Redirect
import java.lang.{ProcessBuilder => JProcessBuilder}

import EtaDependency.EtaVersion
import sbt.Keys._
import sbt._

import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

final case class Etlas(cwd: File, dist: File, etaVersion: EtaVersion) {

  import Etlas._

  def build(cabal: Cabal, log: Logger): Unit = {
    etlas(Seq("build").withBuildDir(dist).withEtaVersion(etaVersion), cwd, log, filterLog = _ => true)
    ()
  }

  def buildArtifacts(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    cabal.getArtifacts(filter).foreach {
      artifact => etlas(Seq("build", artifact.depsPackage).withBuildDir(dist).withEtaVersion(etaVersion), cwd, log, filterLog = _ => true)
    }
  }

  def clean(log: Logger): Unit = {
    etlas(Seq("clean").withBuildDir(dist).withEtaVersion(etaVersion), cwd, log)
    ()
  }

  def deps(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Seq[String] = {
    def filterDepsLog(s: String): Boolean = defaultFilterLog(s) || !(s.startsWith("dependency") || s.startsWith("maven-dependencies"))
    cabal.getArtifacts(filter).flatMap { artifact =>
      etlas(Seq("deps", artifact.depsPackage, "--keep-going").withBuildDir(dist).withEtaVersion(etaVersion), cwd, log, saveOutput = true, filterLog = filterDepsLog)
    }
  }

  def install(log: Logger): Unit = {
    log.info("Installing dependencies...")
    etlas(Seq("install", "--dependencies-only"), cwd, log)
  }

  def freeze(log: Logger): Unit = {
    etlas(Seq("freeze"), cwd, log)
  }

  def run(log: Logger): Unit = {
    etlas(Seq("run").withBuildDir(dist).withEtaVersion(etaVersion), cwd, log)
    ()
  }

  def runArtifacts(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    cabal.getArtifacts(Cabal.Artifact.and(Cabal.Artifact.executable, filter)).foreach { artifact =>
      etlas(Seq("run", artifact.name).withBuildDir(dist).withEtaVersion(etaVersion), cwd, log, filterLog = _ => true)
    }
  }

  def test(log: Logger): Unit = {
    etlas(Seq("test").withBuildDir(dist).withEtaVersion(etaVersion), cwd, log, filterLog = _ => true)
  }

  def testArtifacts(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    cabal.getArtifacts(Cabal.Artifact.and(Cabal.Artifact.testSuite, filter)).foreach { artifact =>
      etlas(Seq("test", artifact.name).withBuildDir(dist).withEtaVersion(etaVersion), cwd, log, filterLog = _ => true)
    }
  }

  // Resolve dependencies

  def getMavenDependencies(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Seq[ModuleID] = {
    log.info("Checking Maven dependencies...")
    parseMavenDeps(deps(cabal, log, filter))
  }

  def getClasspath(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Classpath = {
    log.info("Retrieving Eta dependencies classpath...")
    parseDeps(deps(cabal, log, filter))
      .map(s => PathFinder(file(s)))
      .foldLeft(PathFinder.empty)((s1, s2) => s1 +++ s2)
      .classpath
  }

  def getEtaPackage(cabal: Cabal, log: Logger): Cabal.EtaPackage = {
    log.info("Resolve package dependencies...")
    buildArtifacts(cabal, log, Cabal.Artifact.library)
    Cabal.EtaPackage(cabal.projectName, cabal.getArtifactsJars(dist, etaVersion, Cabal.Artifact.library), getPackageDd(dist, etaVersion))
  }

}

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

  private def logCmd(command: String)(log: Logger): Unit = {
    getParam("etlas.logger.cmd.level") match {
      case Some("INFO") => log.info(command)
      case _ => log.debug(command)
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

    IO.createDirectory(cwd)
    logCmd(s"Running `etlas ${args.mkString(" ")} in '$cwd'`...")(log)
    val exitCode = synchronized(Process("etlas" +: args, cwd) ! logger)

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

    if (saveOutput) lineBuffer else Nil

  }

  private def fork(args: Seq[String], cwd: File, log: sbt.Logger): Unit = {

    logCmd(s"Running `etlas ${args.mkString(" ")} in '$cwd'`...")(Logger(log))

    val jpb = new JProcessBuilder(("etlas" +: args).toArray: _ *)
    jpb.directory(cwd)
    jpb.redirectInput(Redirect.INHERIT)
    val exitCode = Process(jpb).run(SbtUtils.terminalIO).exitValue()

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

  }

  private implicit class ArgsOps(val args: Seq[String]) extends AnyVal {
    def withBuildDir(dist: File): Seq[String] = args ++ Seq("--builddir", dist.getCanonicalPath)
    def withEtaVersion(etaVersion: EtaVersion): Seq[String] = s"--select-eta=${etaVersion.friendlyVersion}" +: args
  }

  def etaVersion(cwd: File, log: Logger): EtaVersion = {
    EtaVersion(etlas(Seq("exec", "eta", "--", "--numeric-version"), cwd, log, saveOutput = true).head)
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
           sourceDir: File,
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
      s"--source-dir=${IO.relativize(cwd, sourceDir).getOrElse(sourceDir.getCanonicalPath)}",
      "--language=Haskell2010"
    ) ++ developers.headOption.toList.flatMap(
      dev => Seq(s"--author=${dev.name}", s"--email=${dev.email}")
    ) ++ homepage.map(
      url => s"--homepage=$url"
    ), cwd, log)
  }

  def repl(cwd: File, dist: File, etaVersion: EtaVersion, log: sbt.Logger): Try[Unit] = {
    def console0(): Unit = {
      log.info("Starting Eta interpreter...")
      fork(Seq("repl").withBuildDir(dist).withEtaVersion(etaVersion), cwd, log)
    }
    Run.executeTrapExit(console0(), log).recover {
      case _: InterruptedException =>
        log.info("Eta REPL was interrupted.")
        ()
    }
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

  def getPackageDd(dist: File, etaVersion: EtaVersion): File = {
    dist / "packagedb" / ("eta-" + etaVersion.machineVersion)
  }

  private def getBuildDir(dist: File, etaVersion: EtaVersion, packageId: String): File = {
    dist / "build" / ("eta-" + etaVersion.machineVersion) / packageId
  }

  def getLibraryJar(dist: File, etaVersion: EtaVersion, packageId: String): File = {
    getBuildDir(dist, etaVersion, packageId) / "build" / (packageId + "-inplace.jar")
  }

  def getExecutableJar(dist: File, etaVersion: EtaVersion, packageId: String, name: String): File = {
    getBuildDir(dist, etaVersion, packageId) / "x" / name / "build" / name / (name + ".jar")
  }

  def getTestSuiteJar(dist: File, etaVersion: EtaVersion, packageId: String, name: String): File = {
    getBuildDir(dist, etaVersion, packageId) / "t" / name / "build" / name / (name + ".jar")
  }

}
