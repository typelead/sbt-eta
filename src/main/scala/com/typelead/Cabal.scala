package com.typelead

import sbt._
import EtaDependency.{EtaPackage, EtaVersion}

final case class Cabal(projectName: String,
                       projectVersion: String,
                       projectLibrary: Option[Cabal.Library],
                       executables: Seq[Cabal.Executable],
                       testSuites: Seq[Cabal.TestSuite]) {

  import Cabal._

  val cabalName: String = projectName + ".cabal"
  val packageId: String = projectName + "-" + projectVersion

  def artifacts: Seq[Artifact[_]] = projectLibrary.toList ++ executables ++ testSuites
  def getArtifacts(filter: Artifact.Filter): Seq[Artifact[_]] = artifacts.filter(filter).sortBy {
    case _: Library    => 0
    case _: Executable => 1
    case _: TestSuite  => 2
  }
  def getArtifactsJars(dist: File, etaVersion: EtaVersion, filter: Artifact.Filter): Seq[File] = {
    getArtifacts(filter).map {
      case _: Library =>
        Etlas.getLibraryJar(dist, etaVersion, packageId)
      case a: Executable =>
        Etlas.getExecutableJar(dist, etaVersion, packageId, a.name)
      case a: TestSuite =>
        Etlas.getTestSuiteJar(dist, etaVersion, packageId, a.name)
    }
  }

  def getMainClass: Option[String] = {
    if (hasExecutable) Some("eta.main")
    else None
  }

  def buildDependencies: Seq[String] = artifacts.flatMap(_.buildDependencies).distinct
  def mavenDependencies: Seq[String] = artifacts.flatMap(_.mavenDependencies).distinct
  def mavenRepositories: Seq[String] = artifacts.flatMap(_.mavenRepositories).distinct
  def gitDependencies: Seq[GitDependency] = distinctBy(artifacts.flatMap(_.gitDependencies))(_.packageName)

  def hasLibrary   : Boolean = projectLibrary.nonEmpty
  def hasExecutable: Boolean = executables.nonEmpty
  def hasTestSuite : Boolean = testSuites.nonEmpty

  def resolveNames: Cabal = this.copy(
    projectLibrary = projectLibrary.map {
      case a if a.name == NONAME => a.copy(name = projectName)
      case other                       => other
    },
    executables = executables.map {
      case a if a.name == NONAME => a.copy(name = projectName)
      case other                       => other
    },
    testSuites = testSuites.map {
      case a if a.name == NONAME => a.copy(name = projectName)
      case other                       => other
    }
  )

  def isEmpty: Boolean = projectName == NONAME || projectVersion == NOVERSION || artifacts.isEmpty

  def getTmpCabal(etaPackages: Seq[EtaPackage]): Cabal = {
    val allBuildDependencies = (buildDependencies ++ etaPackages.flatMap(_.cabal.buildDependencies)).distinct
    val allMavenDependencies = (mavenDependencies ++ etaPackages.flatMap(_.cabal.mavenDependencies)).distinct
    val allMavenRepositories = (mavenRepositories ++ etaPackages.flatMap(_.cabal.mavenRepositories)).distinct
    val allGitDependencies   = distinctBy(gitDependencies ++ etaPackages.flatMap(_.cabal.gitDependencies))(_.packageName)
    Cabal(
      projectName = projectName,
      projectVersion = projectVersion,
      projectLibrary = Some(Library(
        name = projectName,
        sourceDirectories = Nil,
        exposedModules = Nil,
        buildDependencies = allBuildDependencies,
        mavenDependencies = allMavenDependencies,
        mavenRepositories = allMavenRepositories,
        gitDependencies   = allGitDependencies,
        cppOptions = Nil,
        ghcOptions = Nil,
        extensions = Nil,
        includeDirs = Nil,
        installIncludes = Nil,
        language = "Haskell2010"
      )),
      executables = Nil,
      testSuites = Nil
    )
  }

}

object Cabal {

  private val NONAME = "<--noname-->"
  private val NOVERSION = "<--noversion-->"

  private[typelead] val CABAL_PROJECT = "cabal.project"
  private[typelead] val CABAL_PROJECT_LOCAL = "cabal.project.local"
  private[typelead] val CABAL_PROJECT_FREEZE = "cabal.project.freeze"

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
    def mavenRepositories: Seq[String]
    def gitDependencies: Seq[GitDependency]
    def hsMain: Option[String]
    def cppOptions: Seq[String]
    def ghcOptions: Seq[String]
    def includeDirs: Seq[String]
    def installIncludes: Seq[String]
    def extensions: Seq[String]
    def language: String

    def addLibrary(artifact: Option[Library]): A
  }

  final case class Library(override val name: String,
                           override val sourceDirectories: Seq[String],
                           override val exposedModules: Seq[String],
                           override val buildDependencies: Seq[String],
                           override val mavenDependencies: Seq[String],
                           override val mavenRepositories: Seq[String],
                           override val gitDependencies: Seq[GitDependency],
                           override val cppOptions: Seq[String],
                           override val ghcOptions: Seq[String],
                           override val includeDirs: Seq[String],
                           override val installIncludes: Seq[String],
                           override val extensions: Seq[String],
                           override val language: String) extends Artifact[Library] {

    override val depsPackage: String = "lib:" + name
    override val hsMain: Option[String] = None

    override def addLibrary(artifact: Option[Library]): Library = this

  }

  final case class Executable(override val name: String,
                              override val sourceDirectories: Seq[String],
                              override val buildDependencies: Seq[String],
                              override val mavenDependencies: Seq[String],
                              override val mavenRepositories: Seq[String],
                              override val gitDependencies: Seq[GitDependency],
                              override val hsMain: Option[String],
                              override val cppOptions: Seq[String],
                              override val ghcOptions: Seq[String],
                              override val includeDirs: Seq[String],
                              override val installIncludes: Seq[String],
                              override val extensions: Seq[String],
                              override val language: String) extends Artifact[Executable] {

    override val depsPackage: String = "exe:" + name
    override val exposedModules: Seq[String] = Nil

    override def addLibrary(artifact: Option[Library]): Executable = this.copy(buildDependencies = buildDependencies ++ artifact.map(_.name).toList)

  }

  final case class TestSuite(override val name: String,
                             override val sourceDirectories: Seq[String],
                             override val buildDependencies: Seq[String],
                             override val mavenDependencies: Seq[String],
                             override val mavenRepositories: Seq[String],
                             override val gitDependencies: Seq[GitDependency],
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

    override def addLibrary(artifact: Option[Library]): TestSuite = this.copy(buildDependencies = buildDependencies ++ artifact.map(_.name).toList)

  }

  object Artifact {

    type Filter = Artifact[_] => Boolean

    def lib(name: String) : Library    = Library   (name, Nil, Nil, Nil, Nil, Nil, Nil,       Nil, Nil, Nil, Nil, Nil, "Haskell2010")
    def exe(name: String) : Executable = Executable(name, Nil,      Nil, Nil, Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, "Haskell2010")
    def test(name: String): TestSuite  = TestSuite (name, Nil,      Nil, Nil, Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, "Haskell2010", TestSuiteTypes.exitcode)

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

  private def writeArtifact[A <: Artifact[A]](artifact: Artifact[A], etaPackages: Seq[EtaPackage]): Seq[String] = {
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
      writeLines(artifact.buildDependencies ++ artifact.gitDependencies.map(_.packageName) ++ etaPackages.map(_.name).distinct,
        "  build-depends:      ", "                    , ") ++
      writeLines(artifact.mavenDependencies,
        "  maven-depends:      ", "                    , ") ++
      writeLines(artifact.mavenRepositories.filter(_ => artifact.mavenDependencies.nonEmpty),
        "  maven-repos:        ", "                    , ") ++
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

  // --- Write cabal.project file
  def writeCabalProject(cwd: File, cabal: Cabal, etaPackages: Seq[EtaPackage], log: Logger): Unit = {
    log.info(s"Rewrite '$CABAL_PROJECT' in '${cwd.getCanonicalFile}'.")
    val lines = Seq("packages: .", "") ++
      writeLines(etaPackages.map(_.packageDb.getCanonicalPath), "package-dbs:\n  ", "  ") ++
      cabal.gitDependencies.flatMap {
        case GitDependency(_, location, resolver, subDir) =>
          Seq(
            "source-repository-package",
            "  type: git",
            "  location: " + location,
            resolver match {
              case GitDependency.Commit(commit) =>
                "  commit: " + commit
              case GitDependency.Branch(branch) =>
                "  branch: " + branch
              case GitDependency.Tag(tag) =>
                "  tag: " + tag
            }
          ) ++ subDir.map(dir => "  subdir: " + dir) :+ ""
      }
    IO.writeLines(cwd / CABAL_PROJECT, lines)
  }

  // --- Write cabal.project.local file
  def writeCabalProjectLocal(cwd: File, cabal: Cabal, etaPackages: Seq[EtaPackage], classpath: Seq[File], log: Logger): Unit = {
    log.info(s"Rewrite '$CABAL_PROJECT_LOCAL' in '${cwd.getCanonicalFile}'.")
    val fullClasspath = etaPackages.flatMap(_.jars) ++ classpath
    val lines = Seq(
      s"""package ${cabal.projectName}""",
      s"""  eta-options: -cp "${fullClasspath.map(_.getCanonicalPath).mkString(":")}" """
    )
    IO.writeLines(cwd / CABAL_PROJECT_LOCAL, lines)
  }

  def writeCabal(cwd: File, cabal: Cabal, etaPackages: Seq[EtaPackage], log: Logger): Unit = {
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
        ) ++ writeArtifact(artifact, etaPackages)
      }.getOrElse(Nil)
      val executableDefs = cabal.executables.flatMap { artifact =>
        Seq(
          "",
          "executable " + artifact.name
        ) ++ writeArtifact(artifact.addLibrary(cabal.projectLibrary), etaPackages)
      }
      val testSuiteDefs = cabal.testSuites.flatMap { artifact =>
        Seq(
          "",
          "test-suite " + artifact.name
        ) ++ writeArtifact(artifact.addLibrary(cabal.projectLibrary), etaPackages)
      }
      val lines = headers ++ libraryDefs ++ executableDefs ++ testSuiteDefs

      IO.writeLines(cwd / cabal.cabalName, lines)
    }
  }

  def getCabalFile(cwd: File): Option[String] = {
    cwd.listFiles.map(_.getName).find(_.matches(""".*\.cabal$"""))
  }

  private def distinctBy[A, B](xs: Seq[A])(f: A => B): Seq[A] = {
    xs.groupBy(f).mapValues(_.head).values.toIndexedSeq
  }

}