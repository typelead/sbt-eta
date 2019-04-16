package com.typelead

import sbt._

final case class Cabal(projectName: String,
                       projectVersion: String,
                       projectLibrary: Option[Cabal.Library],
                       executables: Seq[Cabal.Executable],
                       testSuites: Seq[Cabal.TestSuite]) {

  val cabalName: String = projectName + ".cabal"
  val packageId: String = projectName + "-" + projectVersion

  def artifacts: Seq[Cabal.Artifact[_]] = projectLibrary.toList ++ executables ++ testSuites
  def getArtifacts(filter: Cabal.Artifact.Filter): Seq[Cabal.Artifact[_]] = artifacts.filter(filter).sortBy {
    case _: Cabal.Library    => 0
    case _: Cabal.Executable => 1
    case _: Cabal.TestSuite  => 2
  }
  def getArtifactsJars(dist: File, etaVersion: String, filter: Cabal.Artifact.Filter): Seq[File] = {
    val buildPath = dist / "build" / ("eta-" + etaVersion) / packageId
    getArtifacts(filter).map {
      case _: Cabal.Library =>
        buildPath / "build" / (packageId + "-inplace.jar")
      case a: Cabal.Executable =>
        buildPath / "x" / a.name / "build" / a.name / (a.name + ".jar")
      case a: Cabal.TestSuite =>
        buildPath / "t" / a.name / "build" / a.name / (a.name + ".jar")
    }
  }

  def getMainClass: Option[String] = {
    if (hasExecutable) Some("eta.main")
    else None
  }

  def hasLibrary   : Boolean = projectLibrary.nonEmpty
  def hasExecutable: Boolean = executables.nonEmpty
  def hasTestSuite : Boolean = testSuites.nonEmpty

  def resolveNames: Cabal = this.copy(
    projectLibrary = projectLibrary.map {
      case a if a.name == Cabal.NONAME => a.copy(name = projectName)
      case other                       => other
    },
    executables = executables.map {
      case a if a.name == Cabal.NONAME => a.copy(name = projectName)
      case other                       => other
    },
    testSuites = testSuites.map {
      case a if a.name == Cabal.NONAME => a.copy(name = projectName)
      case other                       => other
    }
  )

  def isEmpty: Boolean = projectName == Cabal.NONAME || projectVersion == Cabal.NOVERSION || artifacts.isEmpty

}

object Cabal {

  val NONAME = "<--noname-->"
  val NOVERSION = "<--noversion-->"

  val empty: Cabal = Cabal(
    projectName = NONAME,
    projectVersion = NOVERSION,
    projectLibrary = None,
    executables = Nil,
    testSuites = Nil
  )

  object TestSuiteTypes extends Enumeration {
    val exitcode: Value = Value("exitcode-stdio-1.0")
    val detailed: Value = Value("detailed-0.9")
  }

  sealed trait Artifact[A <: Artifact[A]] {
    def name: String
    def depsPackage: String
    def sourceDirectories: Seq[String]
    def exposedModules: Seq[String]
    def buildDependencies: Seq[String]
    def mavenDependencies: Seq[String]
    def hsMain: Option[String]
    def cppOptions: Seq[String]
    def ghcOptions: Seq[String]
    def includeDirs: Seq[String]
    def installIncludes: Seq[String]
    def extensions: Seq[String]
    def language: String

    def addLibrary(artifact: Library): A
  }

  final case class Library(override val name: String,
                           override val sourceDirectories: Seq[String],
                           override val exposedModules: Seq[String],
                           override val buildDependencies: Seq[String],
                           override val mavenDependencies: Seq[String],
                           override val cppOptions: Seq[String],
                           override val ghcOptions: Seq[String],
                           override val includeDirs: Seq[String],
                           override val installIncludes: Seq[String],
                           override val extensions: Seq[String],
                           override val language: String) extends Artifact[Library] {

    override val depsPackage: String = "lib:" + name
    override val hsMain: Option[String] = None

    override def addLibrary(artifact: Library): Library = this

  }

  final case class Executable(override val name: String,
                              override val sourceDirectories: Seq[String],
                              override val buildDependencies: Seq[String],
                              override val mavenDependencies: Seq[String],
                              override val hsMain: Option[String],
                              override val cppOptions: Seq[String],
                              override val ghcOptions: Seq[String],
                              override val includeDirs: Seq[String],
                              override val installIncludes: Seq[String],
                              override val extensions: Seq[String],
                              override val language: String) extends Artifact[Executable] {

    override val depsPackage: String = "exe:" + name
    override val exposedModules: Seq[String] = Nil

    override def addLibrary(artifact: Library): Executable = this.copy(buildDependencies = buildDependencies :+ artifact.name)

  }

  final case class TestSuite(override val name: String,
                             override val sourceDirectories: Seq[String],
                             override val buildDependencies: Seq[String],
                             override val mavenDependencies: Seq[String],
                             override val hsMain: Option[String],
                             override val cppOptions: Seq[String],
                             override val ghcOptions: Seq[String],
                             override val includeDirs: Seq[String],
                             override val installIncludes: Seq[String],
                             override val extensions: Seq[String],
                             override val language: String,
                             testSuiteType: TestSuiteTypes.Value) extends Artifact[TestSuite] {

    override val depsPackage: String = "test:" + name
    override val exposedModules: Seq[String] = Nil

    override def addLibrary(artifact: Library): TestSuite = this.copy(buildDependencies = buildDependencies :+ artifact.name)

  }

  object Artifact {

    type Filter = Artifact[_] => Boolean

    def lib(name: String) : Library    = Library   (name, Nil, Nil, Nil, Nil,       Nil, Nil, Nil, Nil, Nil, "Haskell2010")
    def exe(name: String) : Executable = Executable(name, Nil,      Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, "Haskell2010")
    def test(name: String): TestSuite  = TestSuite (name, Nil,      Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, "Haskell2010", TestSuiteTypes.exitcode)

    val all: Filter = _ => true
    val library: Filter = {
      case _: Library => true
      case _ => false
    }
    val executable: Filter = {
      case _: Executable => true
      case _ => false
    }
    val testSuite: Filter = {
      case _: TestSuite => true
      case _ => false
    }

    def not(filter: Filter): Filter = a => !filter(a)
    def and(f1: Filter, f2: Filter): Filter = a => f1(a) && f2(a)
    def or (f1: Filter, f2: Filter): Filter = a => f1(a) || f2(a)

  }

  def parseCabal(cwd: File, log: Logger): Cabal = {
    getCabalFile(cwd) match {
      case Some(cabalName) =>

        log.info(s"Found '$cabalName' in '${cwd.getCanonicalFile}'.")

        val NamePattern = """\s*name:\s*(\S+)\s*$""".r
        val VersionPattern = """\s*version:\s*(\S+)\s*$""".r
        val LibraryPattern = """\s*library(\s*)$""".r
        val ExecutableWithName = """\s*executable\s*(\S+)\s*$""".r
        val ExecutableWithoutName = """\s*executable(\s*)$""".r
        val TestSuiteWithName = """\s*test-suite\s*(\S+)\s*$""".r
        val TestSuiteWithoutName = """\s*test-suite(\s*)$""".r

        val cabal = IO.readLines(cwd / cabalName).foldLeft(empty) {
          case (info, NamePattern(projName))    => info.copy(projectName = projName)
          case (info, VersionPattern(projVer))  => info.copy(projectVersion = projVer)
          case (info, LibraryPattern(_))        => info.copy(projectLibrary = Some(Artifact.lib(NONAME)))
          case (info, ExecutableWithName(exe))  => info.copy(executables = info.executables :+ Artifact.exe(exe))
          case (info, ExecutableWithoutName(_)) => info.copy(executables = info.executables :+ Artifact.exe(NONAME))
          case (info, TestSuiteWithName(suite)) => info.copy(testSuites = info.testSuites :+ Artifact.test(suite))
          case (info, TestSuiteWithoutName(_))  => info.copy(testSuites = info.testSuites :+ Artifact.test(NONAME))
          case (info, _)                        => info
        }.resolveNames

        if (cabal.projectName == NONAME) {
          log.error("No project name specified.")
          empty
        } else if (cabal.projectVersion == NOVERSION) {
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

  private def getMainTag[A <: Artifact[A]](artifact: Artifact[A]): String = artifact match {
    case t: TestSuite if t.testSuiteType == TestSuiteTypes.exitcode =>
      "  main-is:            "
    case t: TestSuite if t.testSuiteType == TestSuiteTypes.detailed =>
      "  test-module:        "
    case _ =>
      "  main-is:            "
  }

  private def writeLines(lines: Traversable[String], prefix: String, separator: String): Seq[String] = {
    if (lines.nonEmpty) (prefix + lines.head) +: lines.tail.map(separator + _).toList
    else Nil
  }

  private def writeArtifact[A <: Artifact[A]](artifact: Artifact[A]): Seq[String] = {
    val lines = artifact match {
      case t: TestSuite =>
        Seq("  type:               " + t.testSuiteType.toString)
      case _ =>
        Nil
    }
    lines ++
      artifact.hsMain.map(m => getMainTag(artifact) + m).toList ++
      writeLines(artifact.sourceDirectories,
        "  hs-source-dirs:     ", "                    , ") ++
      writeLines(artifact.exposedModules   ,
        "  exposed-modules:    ", "                    , ") ++
      writeLines(artifact.buildDependencies,
        "  build-depends:      ", "                    , ") ++
      writeLines(artifact.mavenDependencies,
        "  maven-depends:      ", "                    , ") ++
      writeLines(Some(artifact.cppOptions).filter(_.nonEmpty).map(_.mkString(" ")),
        "  cpp-options:        ", "                    , ") ++
      writeLines(Some(artifact.ghcOptions).filter(_.nonEmpty).map(_.mkString(" ")),
        "  ghc-options:        ", "") ++
      writeLines(artifact.includeDirs,
        "  include-dirs:       ", "                    , ") ++
      writeLines(artifact.installIncludes,
        "  install-includes:   ", "                    , ") ++
      writeLines(artifact.extensions,
        "  default-extensions: ", "                    , ") ++
      Seq(
        "  default-language:   " + artifact.language
      )
  }

  def writeCabal(cwd: File, cabal: Cabal, log: Logger): Cabal = {
    if (cabal.isEmpty) {
      sys.error("The Eta project is not properly configured.")
    } else {

      log.info(s"Rewrite '${cabal.cabalName}' in '${cwd.getCanonicalFile}'.")

      val headers = Seq(
        "name:          " + cabal.projectName,
        "version:       " + cabal.projectVersion,
        "build-type:    Simple",
        "cabal-version: >= 1.10"
      )
      val libraryDefs = cabal.projectLibrary.map { artifact =>
        Seq(
          "",
          "library"
        ) ++ writeArtifact(artifact)
      }.getOrElse(Nil)
      val executableDefs = cabal.executables.flatMap { artifact =>
        Seq(
          "",
          "executable " + artifact.name
        ) ++ writeArtifact(artifact)
      }
      val testSuiteDefs = cabal.testSuites.flatMap { artifact =>
        Seq(
          "",
          "test-suite " + artifact.name
        ) ++ writeArtifact(artifact)
      }
      val lines = headers ++ libraryDefs ++ executableDefs ++ testSuiteDefs

      IO.writeLines(cwd / cabal.cabalName, lines)

      cabal
    }
  }

  def getCabalFile(cwd: File): Option[String] = {
    cwd.listFiles.map(_.getName).find(_.matches(""".*\.cabal$"""))
  }

}