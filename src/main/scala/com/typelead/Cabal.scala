package com.typelead

import sbt.Keys._
import sbt._

final case class Cabal(name: String,
                       version: String,
                       artifacts: Seq[Cabal.Artifact]) {

  val packageId: String = name + "-" + version

  def addArtifact(artifact: Cabal.Artifact): Cabal = this.copy(artifacts = artifacts :+ artifact)
  def getArtifacts(filter: Cabal.Artifact.Filter): Seq[Cabal.Artifact] = artifacts.filter(filter).sortBy {
    case Cabal.Library(_)    => 0
    case Cabal.Executable(_) => 1
    case Cabal.TestSuite(_)  => 2
  }
  def getArtifactsJars(dist: File, etaVersion: String, filter: Cabal.Artifact.Filter): Classpath = {
    val buildPath = dist / "build" / ("eta-" + etaVersion) / packageId
    getArtifacts(filter).map {
      case Cabal.Library(_) =>
        buildPath / "build" / (packageId + "-inplace.jar")
      case Cabal.Executable(exe) =>
        buildPath / "x" / exe / "build" / exe / (exe + ".jar")
      case Cabal.TestSuite(suite) =>
        buildPath / "t" / suite / "build" / suite / (suite + ".jar")
    }.flatMap(jar => PathFinder(jar).classpath)
  }

  def getMainClass: Option[String] = {
    if (hasExecutable) Some("eta.main")
    else None
  }

  def hasLibrary   : Boolean = getArtifacts(Cabal.Artifact.library).nonEmpty
  def hasExecutable: Boolean = getArtifacts(Cabal.Artifact.executable).nonEmpty
  def hasTestSuite : Boolean = getArtifacts(Cabal.Artifact.testSuite).nonEmpty

  def resolveNames: Cabal = this.copy(
    artifacts = artifacts.map {
      case Cabal.Library(Cabal.NONAME)    => Cabal.Library(name)
      case Cabal.Executable(Cabal.NONAME) => Cabal.Executable(name)
      case Cabal.TestSuite(Cabal.NONAME)  => Cabal.TestSuite(name)
      case other                          => other
    }
  )

}

object Cabal {

  val NONAME = "<--noname-->"
  val NOVERSION = "<--noversion-->"

  val empty: Cabal = Cabal(
    name = NONAME,
    version = NOVERSION,
    artifacts = Nil
  )

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

  def parseCabal(cwd: File, log: Logger): Cabal = {
    getCabalFile(cwd) match {
      case Some(file) =>

        log.info(s"Found '$file' in '${cwd.getCanonicalFile}'.")

        val NamePattern = """\s*name:\s*(\S+)\s*$""".r
        val VersionPattern = """\s*version:\s*(\S+)\s*$""".r
        val LibraryPattern = """\s*library(\s*)$""".r
        val ExecutableWithName = """\s*executable\s*(\S+)\s*$""".r
        val ExecutableWithoutName = """\s*executable(\s*)$""".r
        val TestSuiteWithName = """\s*test-suite\s*(\S+)\s*$""".r
        val TestSuiteWithoutName = """\s*test-suite(\s*)$""".r

        val cabal = IO.readLines(cwd / file).foldLeft(empty) {
          case (info, NamePattern(projName))    => info.copy(name = projName)
          case (info, VersionPattern(projVer))  => info.copy(version = projVer)
          case (info, LibraryPattern(_))        => info.addArtifact(Library(NONAME))
          case (info, ExecutableWithName(exe))  => info.addArtifact(Executable(exe))
          case (info, ExecutableWithoutName(_)) => info.addArtifact(Executable(NONAME))
          case (info, TestSuiteWithName(suite)) => info.addArtifact(TestSuite(suite))
          case (info, TestSuiteWithoutName(_))  => info.addArtifact(TestSuite(NONAME))
          case (info, _)                        => info
        }.resolveNames

        if (cabal.name == NONAME) {
          log.error("No project name specified.")
          empty
        } else if (cabal.version == NOVERSION) {
          log.error("No project version specified.")
          empty
        } else {
          cabal
        }

      case None =>
        log.error(s"No cabal file found in '${cwd.getCanonicalFile}'.")
        empty
    }
  }

  def getCabalFile(cwd: File): Option[String] = {
    cwd.listFiles.map(_.getName).find(_.matches(""".*\.cabal$"""))
  }

}