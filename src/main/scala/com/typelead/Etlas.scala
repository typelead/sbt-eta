package com.typelead

import EtaDependency.EtaVersion
import sbt._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Properties, Try}

final case class Etlas(installPath: Option[File], workDir: File, dist: File, etaVersion: EtaVersion, sendMetrics: Boolean) {

  import Cabal._
  import Etlas._

  def changeWorkDir(workDir: File): Etlas = this.copy(workDir = workDir)

  private def args(xs: String*): Seq[String] = {
    xs.toIndexedSeq.withBuildDir(dist).withEtaVersion(etaVersion).addSendMetrics(sendMetrics)
  }

  def build(cabal: Cabal, log: Logger): Unit = {
    etlas(installPath, args("build"), workDir, log, filterLog = _ => true)
  }

  def buildArtifacts(cabal: Cabal, log: Logger, filter: Artifact.Filter): Unit = {
    cabal.getArtifacts(filter).foreach {
      artifact => etlas(installPath, args("build", artifact.depsPackage), workDir, log, filterLog = _ => true)
    }
  }

  def clean(log: Logger): Unit = {
    etlas(installPath, args("clean"), workDir, log)
  }

  def deps(cabal: Cabal, log: Logger, filter: Artifact.Filter): Seq[EtaPackage.Dependency] = {
    cabal.getArtifacts(filter).flatMap { artifact =>
      depsArtifact(artifact, log)
    }
  }

  def depsArtifact(artifact: Artifact, log: Logger): Seq[EtaPackage.Dependency] = {
    def filterDepsLog(s: String): Boolean = defaultFilterLog(s) || !(s.startsWith("dependency") || s.startsWith("maven-dependencies"))
    log.info(s"Refresh dependencies (${artifact.name}) ...")
    val depsList = etlas(installPath, args("deps", artifact.depsPackage, "--keep-going"), workDir, log, saveOutput = true, filterLog = filterDepsLog)
    parseAllDependencies(depsList)
  }

  def install(log: Logger): Unit = {
    log.info("Installing dependencies...")
    etlas(installPath, args("install", "--dependencies-only"), workDir, log)
  }

  def freeze(log: Logger): Option[File] = {
    etlas(installPath, args("freeze"), workDir, log)
    (workDir * CABAL_PROJECT_FREEZE).get.headOption
  }

  def run(log: Logger): Unit = {
    etlas(installPath, args("run"), workDir, log)
  }

  def runArtifacts(cabal: Cabal, log: Logger, filter: Artifact.Filter): Unit = {
    cabal.getArtifacts(Artifact.and(Artifact.executable, filter)).foreach { artifact =>
      etlas(installPath, args("run", artifact.name), workDir, log, filterLog = _ => true)
    }
  }

  def test(log: Logger): Unit = {
    etlas(installPath, args("test"), workDir, log, filterLog = _ => true)
  }

  def testArtifacts(cabal: Cabal, log: Logger, filter: Artifact.Filter): Unit = {
    cabal.getArtifacts(Artifact.and(Artifact.testSuite, filter)).foreach { artifact =>
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
    SbtUtils.executeTrapExit(console0(), log).recover {
      case _: InterruptedException =>
        log.info("Eta REPL was interrupted.")
        ()
    }
  }

  def getEtaPackage(cabal: Cabal, log: Logger): EtaPackage = {
    log.info("Resolve package dependencies...")
    EtaPackage(
      cabal,
      cabal.getArtifactsJars(dist, etaVersion, Artifact.library),
      getPackageDd(dist, etaVersion),
      cabal.artifacts.map { artifact =>
        artifact.name -> depsArtifact(artifact, log)
      }.toMap
    )
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
    val exitCode = synchronized {
      logCmd(s"Running `$binary ${args.mkString(" ")}` in '$workDir'...")(log)
      Process(binary +: args, workDir) ! logger
    }

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

    if (saveOutput) lineBuffer else Nil

  }

  private def fork(installPath: Option[File], args: Seq[String], workDir: File, log: sbt.Logger): Unit = {

    val binary = getEtlasBinary(installPath)
    logCmd(s"Running `$binary ${args.mkString(" ")}` in '$workDir'...")(Logger(log))
    val exitCode = SbtUtils.execInherited(binary +: args, workDir)

    if (exitCode != 0) {
      sys.error("\n\n[etlas] Exit Failure " ++ exitCode.toString)
    }

  }

  private implicit class ArgsOps(val args: Seq[String]) extends AnyVal {
    def withBuildDir(dist: File): Seq[String] = args ++ Seq("--builddir", dist.getCanonicalPath)
    def withEtaVersion(etaVersion: EtaVersion): Seq[String] = s"--select-eta=${etaVersion.friendlyVersion}" +: args
    def addSendMetrics(flag: Boolean): Seq[String] = (if (flag) "--enable-send-metrics" else "--disable-send-metrics") +: args
  }

  def etaVersion(installPath: Option[File], workDir: File, sendMetrics: Boolean, log: Logger): EtaVersion = {
    val args = Seq("exec", "eta", "--", "--numeric-version").addSendMetrics(sendMetrics)
    EtaVersion(etlas(installPath, args, workDir, log, saveOutput = true).head)
  }

  def etlasVersion(installPath: Option[File], workDir: File, sendMetrics: Boolean, log: Logger): String = {
    val args = Seq("--numeric-version").addSendMetrics(sendMetrics)
    etlas(installPath, args, workDir, log, saveOutput = true).head
  }

  def etaSupported(installPath: Option[File], workDir: File, etaVersion: EtaVersion, sendMetrics: Boolean, log: Logger): Supported = {
    val args = Seq("exec", "eta", "--", "--supported-extensions").withEtaVersion(etaVersion).addSendMetrics(sendMetrics)
    etlas(installPath, args, workDir, log, saveOutput = true)
      .foldLeft(Etlas.Supported(Nil, Nil)) {
        case (Etlas.Supported(languages, extensions), str) if str.startsWith("Haskell") =>
          Etlas.Supported(languages :+ str, extensions)
        case (Etlas.Supported(languages, extensions), str) =>
          Etlas.Supported(languages, extensions :+ str)
      }
  }

  private[typelead] val DEFAULT_ETLAS_REPO = "http://cdnverify.eta-lang.org/eta-binaries"

  @tailrec
  def download(repo: String, dest: File, version: String, sendMetrics: Boolean, log: Logger, errorOnWrongVersion: Boolean = false): Unit = {
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
      SbtUtils.download(url, dest)
    }
    if (dest.setExecutable(true)) {
      val curVersion = etlasVersion(Some(dest), dest.getParentFile, sendMetrics, log)
      if (curVersion == version) ()
      else if (!errorOnWrongVersion) {
        log.warn(s"Wrong version installed (actual: $curVersion, expected: $version). Try to download correct version ...")
        IO.delete(dest)
        download(repo, dest, version, sendMetrics, log, errorOnWrongVersion = true)
      } else {
        log.error(s"Wrong version installed (actual: $curVersion, expected: $version).")
        sys.error(s"Executable '${dest.getCanonicalPath}' is not Etlas binary.")
      }
    } else {
      sys.error("Could not set permissions for Eltas binary.")
    }
  }

  private def parseAllDependencies(allLines: Seq[String]): Seq[EtaPackage.Dependency] = {

    val dependencyPrefix = "dependency,"
    val mavenDepsPrefix = "maven-dependencies,"

    def parseMavenModules(line: String): Seq[ModuleID] = {
      for {
        parts <- line.split(":").grouped(3).toList if parts.length == 3
      } yield parts(0) % parts(1) % parts(2)
    }

    allLines.collect {
      case line if line.startsWith(dependencyPrefix) =>
        val parts = line.replace(dependencyPrefix, "").split(",", 4)
        for {
          name <- parts.lift(0).toList
        } yield EtaPackage.LibraryDependency(
          name,
          parts.lift(1).map(parseMavenModules).getOrElse(Nil),
          parts.lift(2).map(_.split(":").toList).getOrElse(Nil).map(jar => file(jar)),
          parts.lift(3).map(_.split(":").toList).getOrElse(Nil)
        )
      case line if line.startsWith(mavenDepsPrefix) =>
        for {
          module <- parseMavenModules(line.replace(mavenDepsPrefix, ""))
        } yield EtaPackage.MavenDependency(module)
    }.flatten

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
