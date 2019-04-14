package com.typelead

import sbt._

import scala.io.Source

final case class Cabal(name: String,
                       version: String,
                       artifacts: Seq[Cabal.Artifact]) {
  val packageId: String = name + "-" + version
}

object Cabal {

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
    def and(f1: Filter, f2: Filter): Filter = a => f1(a) && f2(a)
    def or (f1: Filter, f2: Filter): Filter = a => f1(a) || f2(a)

  }

  def parseCabal(cwd: File, log: Logger): Option[Cabal] = {
    getCabalFile(cwd) match {
      case Some(cabal) =>
        log.info(s"Found '$cabal' in '${cwd.getCanonicalFile}'.")
        parseProjectName(cwd, cabal) match {
          case Some(proj) => parseProjectVersion(cwd, cabal) match {
            case Some(ver) =>
              Some(Cabal(
                name = proj,
                version = ver,
                artifacts = parseArtifacts(cwd, cabal, proj)
              ))
            case None =>
              log.error("No project version specified.")
              None
          }
          case None =>
            log.error("No project name specified.")
            None
        }
      case None =>
        log.error(s"No cabal file found in '${cwd.getCanonicalFile}'.")
        None
    }
  }

  def getCabalFile(cwd: File): Option[String] = {
    cwd.listFiles.map(_.getName).find(_.matches(""".*\.cabal$"""))
  }

  private def getCabalLines(cwd: File, cabal: String): Iterator[String] = {
    Source.fromFile(cwd / cabal).getLines
  }

  private def parseProjectName(cwd: File, cabal: String): Option[String] = {
    getCabalLines(cwd, cabal)
      .filter(_.matches("""\s*name:\s*\S+\s*$"""))
      .toSeq
      .headOption
      .map(_.split(":")(1))
      .map(_.trim)
  }

  private def parseProjectVersion(cwd: File, cabal: String): Option[String] = {
    getCabalLines(cwd, cabal)
      .filter(_.matches("""\s*version:\s*\S+\s*$"""))
      .toSeq
      .headOption
      .map(_.split(":")(1))
      .map(_.trim)
  }

  private def parseArtifacts(cwd: File, cabal: String, proj: String): Seq[Artifact] = {
    val LibraryPattern = """\s*library(\s*)$""".r
    val ExecutableWithName = """\s*executable\s*(\S+)\s*$""".r
    val ExecutableWithoutName = """\s*executable(\s*)$""".r
    val TestSuiteWithName = """\s*test-suite\s*(\S+)\s*$""".r
    val TestSuiteWithoutName = """\s*test-suite(\s*)$""".r
    getCabalLines(cwd, cabal).collect {
      case LibraryPattern(_)        => Library(proj)
      case ExecutableWithName(exe)  => Executable(exe)
      case ExecutableWithoutName(_) => Executable(proj)
      case TestSuiteWithName(suite) => TestSuite(suite)
      case TestSuiteWithoutName(_)  => TestSuite(proj)
    }.toList.sortBy {
      case Library(_)    => 0
      case Executable(_) => 1
      case TestSuite(_)  => 2
    }
  }

  def getArtifacts(cwd: File, log: Logger, filter: Artifact.Filter): Seq[Artifact] = {
    parseCabal(cwd, log).map(_.artifacts.filter(filter)).getOrElse(Nil)
  }

  def hasExecutable(cwd: File, log: Logger): Boolean = {
    getArtifacts(cwd, log, Artifact.executable).nonEmpty
  }

  def getMainClass(cwd: File, defaultMainClass: Option[String], log: Logger): Option[String] = {
    if (hasExecutable(cwd, log)) {
      Some("eta.main")
    } else {
      defaultMainClass
    }
  }

}