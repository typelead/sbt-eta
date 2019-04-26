package com.typelead

import java.lang.ProcessBuilder.Redirect
import java.lang.{ProcessBuilder => JProcessBuilder}

import EtaDependency.{EtaPackage, EtaVersion}
import sbt.Keys._
import sbt._
import sbt.io.Using

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Properties, Try}

final case class Etlas(installPath: Option[File], workDir: File, dist: File, etaVersion: EtaVersion, sendMetrics: Boolean) {

  import Etlas._

  def changeWorkDir(workDir: File): Etlas = this.copy(workDir = workDir)

  private def args(xs: String*): Seq[String] = {
    xs.toIndexedSeq.withBuildDir(dist).withEtaVersion(etaVersion).addSendMetrics(sendMetrics)
  }

  def build(cabal: Cabal, log: Logger): Unit = {
    etlas(installPath, args("build"), workDir, log, filterLog = _ => true)
  }

  def buildArtifacts(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    cabal.getArtifacts(filter).foreach {
      artifact => etlas(installPath, args("build", artifact.depsPackage), workDir, log, filterLog = _ => true)
    }
  }

  def clean(log: Logger): Unit = {
    etlas(installPath, args("clean"), workDir, log)
  }

  def deps(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Seq[String] = {
    def filterDepsLog(s: String): Boolean = defaultFilterLog(s) || !(s.startsWith("dependency") || s.startsWith("maven-dependencies"))
    cabal.getArtifacts(filter).flatMap { artifact =>
      etlas(installPath, args("deps", artifact.depsPackage, "--keep-going"), workDir, log, saveOutput = true, filterLog = filterDepsLog)
    }
  }

  def install(log: Logger): Unit = {
    log.info("Installing dependencies...")
    etlas(installPath, args("install", "--dependencies-only"), workDir, log)
  }

  def freeze(log: Logger): Option[File] = {
    etlas(installPath, args("freeze"), workDir, log)
    (workDir * Cabal.CABAL_PROJECT_FREEZE).get().headOption
  }

  def run(log: Logger): Unit = {
    etlas(installPath, args("run"), workDir, log)
  }

  def runArtifacts(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    cabal.getArtifacts(Cabal.Artifact.and(Cabal.Artifact.executable, filter)).foreach { artifact =>
      etlas(installPath, args("run", artifact.name), workDir, log, filterLog = _ => true)
    }
  }

  def test(log: Logger): Unit = {
    etlas(installPath, args("test"), workDir, log, filterLog = _ => true)
  }

  def testArtifacts(cabal: Cabal, log: Logger, filter: Cabal.Artifact.Filter): Unit = {
    cabal.getArtifacts(Cabal.Artifact.and(Cabal.Artifact.testSuite, filter)).foreach { artifact =>
      etlas(installPath, args("test", artifact.name), workDir, log, filterLog = _ => true)
    }
  }

  def init(name: String,
           description: String,
           version: String,
           developers: Seq[Developer],
           homepage: Option[URL],
           sourceDir: File,
           log: Logger): Unit = {
    log.info("Initialize project...")
    etlas(installPath, Seq(
      "init",
      "--non-interactive",
      "--is-executable",
      s"--package-dir=${workDir.getCanonicalPath}",
      s"--package-name=$name-eta",
      s"--synopsis=$description",
      s"--version=$version",
      s"--source-dir=${IO.relativize(workDir, sourceDir).getOrElse(sourceDir.getCanonicalPath)}",
      "--language=Haskell2010"
    ) ++ developers.headOption.toList.flatMap(
      dev => Seq(s"--author=${dev.name}", s"--email=${dev.email}")
    ) ++ homepage.map(
      url => s"--homepage=$url"
    ), workDir, log)
  }

  def repl(log: sbt.Logger): Try[Unit] = {
    def console0(): Unit = {
      log.info("Starting Eta interpreter...")
      fork(installPath, args("repl"), workDir, log)
    }
    Run.executeTrapExit(console0(), log).recover {
      case _: InterruptedException =>
        log.info("Eta REPL was interrupted.")
        ()
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

  def getEtaPackage(cabal: Cabal, log: Logger): EtaPackage = {
    log.info("Resolve package dependencies...")
    EtaPackage(cabal, cabal.getArtifactsJars(dist, etaVersion, Cabal.Artifact.library), getPackageDd(dist, etaVersion))
  }

}

object Etlas {

  final case class Supported(languages: Seq[String], extensions: Seq[String])

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

  private def getEtlasBinary(installPath: Option[File]): String = {
    installPath.map(_.getCanonicalPath).getOrElse("etlas")
  }

  private def etlas(installPath: Option[File],
                    args: Seq[String],
                    workDir: File,
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

    IO.createDirectory(workDir)
    val binary = getEtlasBinary(installPath)
    logCmd(s"Running `$binary ${args.mkString(" ")}` in '$workDir'...")(log)
    val exitCode = synchronized(Process(binary +: args, workDir) ! logger)

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

    if (saveOutput) lineBuffer else Nil

  }

  private def fork(installPath: Option[File], args: Seq[String], workDir: File, log: sbt.Logger): Unit = {

    val binary = getEtlasBinary(installPath)
    logCmd(s"Running `$binary ${args.mkString(" ")}` in '$workDir'...")(Logger(log))
    val jpb = new JProcessBuilder((binary +: args).toArray: _ *)
    jpb.directory(workDir)
    jpb.redirectInput(Redirect.INHERIT)
    val exitCode = Process(jpb).run(SbtUtils.terminalIO).exitValue()

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

  }

  private implicit class ArgsOps(val args: Seq[String]) extends AnyVal {
    def withBuildDir(dist: File): Seq[String] = args ++ Seq("--builddir", dist.getCanonicalPath)
    def withEtaVersion(etaVersion: EtaVersion): Seq[String] = s"--select-eta=${etaVersion.friendlyVersion}" +: args
    def addSendMetrics(flag: Boolean): Seq[String] = (if (flag) "--enable-send-metrics" else "--disable-send-metrics") +: args
  }

  def etaVersion(installPath: Option[File], workDir: File, log: Logger): EtaVersion = {
    EtaVersion(etlas(installPath, Seq("exec", "eta", "--", "--numeric-version"), workDir, log, saveOutput = true).head)
  }

  def etlasVersion(installPath: Option[File], workDir: File, log: Logger): String = {
    etlas(installPath, Seq("--numeric-version"), workDir, log, saveOutput = true).head
  }

  def etaSupported(installPath: Option[File], workDir: File, etaVersion: EtaVersion, log: Logger): Supported = {
    etlas(installPath, Seq("exec", "eta", "--", "--supported-extensions").withEtaVersion(etaVersion), workDir, log, saveOutput = true)
      .foldLeft(Etlas.Supported(Nil, Nil)) {
        case (Etlas.Supported(languages, extensions), str) if str.startsWith("Haskell") =>
          Etlas.Supported(languages :+ str, extensions)
        case (Etlas.Supported(languages, extensions), str) =>
          Etlas.Supported(languages, extensions :+ str)
      }
  }

  private[typelead] val DEFAULT_ETLAS_REPO = "http://cdnverify.eta-lang.org/eta-binaries"

  @tailrec
  def download(repo: String, dest: File, version: String, log: Logger, errorOnWrongVersion: Boolean = false): Unit = {
    val (arch, ext) = if (Properties.isWin)
      ("x86_64-windows", ".exe")
    else if (Properties.isMac)
      ("x86_64-osx", "")
    else
      ("x86_64-linux", "")
    val binary = "etlas" + ext
    if (dest.exists()) {
      ()
    } else {
      val url = new URL(repo + "/etlas-" + version + "/binaries/" + arch + "/" + binary)
      log.info(s"Downloading Etlas binary from '$url' to '${dest.getCanonicalPath}' ...")
      IO.createDirectory(dest.getParentFile)
      Using.urlInputStream(url) { input =>
        IO.transfer(input, dest)
      }
    }
    if (dest.setExecutable(true)) {
      val curVersion = etlasVersion(Some(dest), dest.getParentFile, log)
      if (curVersion == version) ()
      else if (!errorOnWrongVersion) {
        log.warn(s"Wrong version installed (actual: $curVersion, expected: $version). Try to download correct version ...")
        IO.delete(dest)
        download(repo, dest, version, log, errorOnWrongVersion = true)
      } else {
        log.error(s"Wrong version installed (actual: $curVersion, expected: $version).")
        sys.error(s"Executable '${dest.getCanonicalPath}' is not Etlas binary.")
      }
    } else {
      sys.error("Could not set permissions for Eltas binary.")
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
