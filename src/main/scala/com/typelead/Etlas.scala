package com.typelead

import sbt._
import Keys._

import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}

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
        lineBuffer += s
        if (filterLog(s)) {
          log.info(s)
        }
      }
      override def err(s: => String): Unit = {
        lineBuffer += s
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

  // Commands

  def build(cwd: File, dist: String, log: Logger): Unit = {
    etlas(Seq("build", "--builddir", dist), cwd, log, filterLog = _ => true)
    ()
  }

  def buildArtifacts(cwd: File, dist: String, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    Cabal.getArtifacts(cwd, log, filter).foreach {
      artifact => etlas(Seq("build", artifact.depsPackage, "--builddir", dist), cwd, log, filterLog = _ => true)
    }
  }

  def clean(cwd: File, dist: String, log: Logger): Unit = {
    etlas(Seq("clean", "--builddir", dist), cwd, log)
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

  def install(cwd: File, log: Logger): Unit = {
    log.info("Installing dependencies...")
    etlas(Seq("install", "--dependencies-only"), cwd, log)
  }

  def run(cwd: File, dist: String, log: Logger): Unit = {
    etlas(Seq("run", "--builddir", dist), cwd, log)
    ()
  }

  def runArtifacts(cwd: File, dist: String, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Unit = {
    Cabal.getArtifacts(cwd, log, Cabal.Artifact.and(Cabal.Artifact.executable, filter)).foreach { artifact =>
      etlas(Seq("run", artifact.name, "--builddir", dist), cwd, log, filterLog = _ => true)
    }
  }

  def test(cwd: File, dist: String, log: Logger): Unit = {
    etlas(Seq("test", "--builddir", dist), cwd, log, filterLog = _ => true)
  }

  def testArtifacts(cwd: File, dist: String, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Unit = {
    Cabal.getArtifacts(cwd, log, Cabal.Artifact.and(Cabal.Artifact.testSuite, filter)).foreach { artifact =>
      etlas(Seq("test", artifact.name, "--builddir", dist), cwd, log, filterLog = _ => true)
    }
  }

  // Resolve dependencies

  private def getArtifactsJars(cwd: File, dist: String, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Classpath = {
    Cabal.parseCabal(cwd, log).map { cabal =>
      val buildPath = file(dist) / "build" / ("eta-" + Etlas.etaVersion(cwd, log)) / cabal.packageId
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

  def getFullClasspath(cwd: File, dist: String, log: Logger, filter: Cabal.Artifact.Filter = Cabal.Artifact.all): Classpath = {
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
