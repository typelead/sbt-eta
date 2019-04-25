package com.typelead

import sbt.Keys._
import sbt.{Def, _}
import EtaDependency.{EtaPackage, EtaVersion}

object SbtEta extends AutoPlugin {

  import Cabal._

  override def requires = plugins.JvmPlugin
  override def trigger  = noTrigger

  override def buildSettings: Seq[Def.Setting[_]] = buildEtaSettings
  override def projectSettings: Seq[Def.Setting[_]] = baseProjectSettings
  override def projectConfigurations: Seq[Configuration] = Seq(autoImport.Eta, autoImport.EtaLib, autoImport.EtaExe, autoImport.EtaTest)

  object autoImport {

    lazy val Eta: Configuration = config("Eta")
    lazy val EtaLib: Configuration = config("EtaLib")
    lazy val EtaExe: Configuration = config("EtaExe")
    lazy val EtaTest: Configuration = config("EtaTest")

    lazy val etaVersion      = settingKey[String]("Version of the Eta compiler.")
    lazy val etlasVersion    = settingKey[String]("Version of the Etlas build tool.")
    lazy val etlasUseLocal   = settingKey[Boolean]("If `true`, use instance of Etlas installed in your system. If `false`, use Etlas specified by project settings.")
    lazy val etlasPath       = settingKey[File]("Specifies the path to Etlas executable used in this build.")
    lazy val etlasRepository = settingKey[String]("URL address of Etlas repository. Do not change!")
    lazy val etaSendMetrics  = settingKey[Boolean]("Would you like to help us make Eta the fastest growing programming language, and help pure functional programming become mainstream?")

    lazy val etaCompile      = taskKey[Unit]("Build your Eta project.")

    // Eta configuration DSL

    lazy val useLocalCabal = settingKey[Boolean]("If `true`, use local .cabal file in root folder. If `false`, recreate .cabal file from project settings.")
    lazy val hsMain = settingKey[Option[String]]("Specifies main class for artifact.")
    lazy val exposedModules = settingKey[Seq[String]]("A list of modules added by this package.")
    lazy val language = settingKey[String]("Specifies the language to use for the build.")
    lazy val extensions = settingKey[Seq[String]]("The set of language extensions to enable or disable for the build.")
    lazy val cppOptions = settingKey[Seq[String]]("The flags to send to the preprocessor used by the Eta compiler to preprocess files that enable the CPP extension.")
    lazy val ghcOptions = settingKey[Seq[String]]("The direct flags to send to the Eta compiler.")
    lazy val includeDirs = settingKey[Seq[File]]("Paths to directories which contain include files that can later be referenced with `#include` directives.")
    lazy val installIncludes = settingKey[Seq[String]]("Names of include files to install along with the package being built.")
    lazy val testSuiteType = settingKey[Cabal.TestSuiteTypes.Value]("The interface type and version of the test suite.")
    lazy val gitDependencies = settingKey[Seq[GitDependency]]("List of external dependencies, which are build and installed from Git.")

    def eta(packageName: String): ModuleID = EtaDependency(packageName)
    def eta(packageName: String, version: String): ModuleID = EtaDependency(packageName, version)

    def branch(branch: String): GitDependency.Resolver = GitDependency.Branch(branch)
    def commit(commit: String): GitDependency.Resolver = GitDependency.Commit(commit)
    def tag(tag: String)      : GitDependency.Resolver = GitDependency.Tag(tag)

    def git(packageName: String, location: String, resolver: GitDependency.Resolver): GitDependency =
      GitDependency(packageName, location, resolver, None)
    def git(packageName: String, location: String, resolver: GitDependency.Resolver, subDir: String) =
      GitDependency(packageName, location, resolver, Some(subDir))

    val Haskell98 = "Haskell98"
    val Haskell2010 = "Haskell2010"

    val exitcodeTestSuite: Cabal.TestSuiteTypes.Value = Cabal.TestSuiteTypes.exitcode
    val detailedTestSuite: Cabal.TestSuiteTypes.Value = Cabal.TestSuiteTypes.detailed

  }

  import autoImport._

  private lazy val etlas = settingKey[Etlas]("Helper for Etlas commands.")
  private lazy val etaCabal = taskKey[Cabal]("Structure of the .cabal file.")
  private lazy val etaPackage = taskKey[EtaPackage]("Structure of Eta package.")
  private lazy val etaSupported = settingKey[Etlas.Supported]("Supported languages and extensions.")

  private lazy val buildEtaSettings: Seq[Def.Setting[_]] = {
    inThisBuild(Seq(
      etaSendMetrics := true,
      etlasUseLocal := true,
      etlasPath := BuildPaths.outputDirectory(BuildPaths.projectStandard(baseDirectory.value)) / "etlas" / "etlas",
      etlasRepository := Etlas.DEFAULT_ETLAS_REPO,
      etlasVersion := {
        val installPath = if (etlasUseLocal.value) None else Some(etlasPath.value)
        Etlas.etlasVersion(installPath, baseDirectory.value, Logger(sLog.value))
      },
      etaVersion := {
        val installPath = if (etlasUseLocal.value) None else Some(etlasPath.value)
        Etlas.etaVersion(installPath, baseDirectory.value, Logger(sLog.value)).friendlyVersion
      }
    ))
  }

  private lazy val baseEtaSettings: Seq[Def.Setting[_]] = {
    inConfig(Eta)(Seq(
      baseDirectory := (target in Compile).value / "eta",
      target := (target in Compile).value / "eta" / "dist",
      // Plugin specific tasks
      etlas := getEtlasSetting.value,
      etaCabal := refreshCabalTask.value,
      etaPackage := {
        etlas.value.getEtaPackage(etaCabal.value, Logger(streams.value))
      },
      etaSupported := {
        etlas.value.getSupported(Logger(sLog.value))
      },
      // Standard tasks
      clean := {
        etlas.value.clean(Logger(streams.value))
      },
      run := {
        etlas.value.runArtifacts(etaCabal.value, Logger(streams.value), Artifact.all)
      },
      test := {
        etlas.value.testArtifacts(etaCabal.value, Logger(streams.value), Artifact.all)
      },
      mainClass := {
        (etaCompile in Compile).value
        etaCabal.value.getMainClass
      },
      projectDependencies := {
        etlas.value.getMavenDependencies(etaCabal.value, Logger(streams.value), Artifact.not(Artifact.testSuite)) ++
        etlas.value.getMavenDependencies(etaCabal.value, Logger(streams.value), Artifact.testSuite).map(_ % Test)
      },
      // DSL
      useLocalCabal := false,
      language := Haskell2010,
      extensions := Nil,
      cppOptions := Nil,
      ghcOptions := Nil,
      includeDirs := Nil,
      installIncludes := Nil,
      testSuiteType := exitcodeTestSuite,
      libraryDependencies := Seq(EtaDependency.base),
      gitDependencies := Nil
    )) ++
      makeSettings(EtaLib, Compile, Artifact.library) ++
      makeSettings(EtaExe, Compile, Artifact.executable) ++
      makeSettings(EtaTest,   Test, Artifact.or(Artifact.library, Artifact.testSuite))
  }

  private def makeSettings(config: Configuration, base: Configuration, filter: Artifact.Filter): Seq[Def.Setting[_]] = {
    inConfig(config)(Seq(
      sourceDirectory := (sourceDirectory in base).value / "eta",
      sourceDirectories := Seq(sourceDirectory.value),
      exportedProductJars := {
        val log = Logger(streams.value)
        (etaCompile in base).value
        (etaCabal in Eta).value.getArtifactsJars((target in Eta).value, getEtaVersion.value, filter).flatMap { jar =>
          log.info("Eta artifact JAR: " + jar.getCanonicalPath)
          PathFinder(jar).classpath
        }
      },
      managedClasspath := {
        (etlas in Eta).value.getClasspath((etaCabal in Eta).value, Logger(streams.value), filter)
      },
      // DSL
      hsMain := None,
      exposedModules := Nil,
      language := (language in Eta).value,
      extensions := (extensions in Eta).value,
      cppOptions := (cppOptions in Eta).value,
      ghcOptions := (ghcOptions in Eta).value,
      includeDirs := (includeDirs in Eta).value,
      installIncludes := (installIncludes in Eta).value,
      testSuiteType := (testSuiteType in Eta).value,
      libraryDependencies := (libraryDependencies in Eta).value,
      gitDependencies := (gitDependencies in Eta).value
    ))
  }

  lazy val baseProjectSettings: Seq[Def.Setting[_]] = baseEtaSettings ++ Seq(

    // Specific Eta tasks

    etaCompile in Compile := {
      (etlas in Eta).value.build((etaCabal in Eta).value, Logger(streams.value))
    },
    etaCompile in Test := {
      (etlas in Eta).value.buildArtifacts((etaCabal in Eta).value, Logger(streams.value), Artifact.testSuite)
    },

    // Standard tasks override

    clean := {
      (clean in Eta).value
      clean.value
    },
    update := {
      (projectDependencies in Eta).value
      update.value
    },

    projectDependencies ++= (projectDependencies in Eta).value,

    libraryDependencies ++= EtaDependency.getAllMavenDependencies((libraryDependencies in EtaLib).value),
    libraryDependencies ++= EtaDependency.getAllMavenDependencies((libraryDependencies in EtaExe).value),
    libraryDependencies ++= EtaDependency.getAllMavenDependencies((libraryDependencies in EtaTest).value).map(_ % Test),

    unmanagedJars in Compile ++= (managedClasspath in EtaLib).value,
    unmanagedJars in Compile ++= (exportedProductJars in EtaLib).value,
    exportedProductJars in Compile ++= (exportedProductJars in EtaLib).value,

    unmanagedJars in Runtime ++= (managedClasspath in EtaExe).value,
    unmanagedJars in Runtime ++= (exportedProductJars in EtaExe).value,
    exportedProductJars in Runtime ++= (exportedProductJars in EtaExe).value,

    unmanagedJars in Test ++= (managedClasspath in EtaTest).value,
    unmanagedJars in Test ++= (exportedProductJars in EtaTest).value,
    exportedProductJars in Test ++= (exportedProductJars in EtaTest).value,

    compile in Compile := {
      (etaCompile in Compile).value
      (compile in Compile).value
    },
    compile in Test := {
      (etaCompile in Test).value
      (compile in Test).value
    },
    test in Test := {
      (test in Eta).value
      (test in Test).value
    },

    mainClass in (Compile, run) := {
      (mainClass in Eta).value orElse (mainClass in (Compile, run)).value
    },
    mainClass in (Compile, packageBin) := {
      (mainClass in Eta).value orElse (mainClass in (Compile, packageBin)).value
    },

    watchSources ++= ((sourceDirectory in EtaLib).value ** "*").get(),
    watchSources ++= ((sourceDirectory in EtaExe).value ** "*").get(),
    watchSources ++= ((sourceDirectory in EtaTest).value ** "*").get(),

    commands ++= Seq(etaInitCommand, etaReplCommand, etaLanguages, etaExtensions)
  )

  private def getEtaVersion: Def.Initialize[EtaVersion] = Def.setting {
    EtaVersion((etaVersion in ThisBuild).value)
  }

  private def getEtlasSetting: Def.Initialize[Etlas] = Def.setting {
    // Global settings
    val useLocal = (etlasUseLocal in ThisBuild).value
    val etlasVer = (etlasVersion in ThisBuild).value
    val etlasRepo = (etlasRepository in ThisBuild).value
    val etaVer = getEtaVersion.value
    val installPath = (etlasPath in ThisBuild).value
    val sendMetricsFlag = (etaSendMetrics in ThisBuild).value
    // Project settings
    val workDir = baseDirectory.value
    val dist = target.value
    val log = Logger(sLog.value)
    // Configure Etlas
    if (useLocal) {
      Etlas(None, workDir, dist, etaVer, sendMetricsFlag)
    } else {
      Etlas.download(etlasRepo, installPath, etlasVer, log)
      Etlas(Some(installPath), workDir, dist, etaVer, sendMetricsFlag)
    }
  }

  private def getFilePaths(workDir: File, files: Seq[File]): Seq[String] = {
    files.map { file =>
      IO.relativize(workDir, file).getOrElse(file.getCanonicalPath)
    }
  }

  private def getEtaBuildDependencies(dependencies: Seq[ModuleID]): Seq[String] = {
    EtaDependency.getAllEtaDependencies(dependencies).map(EtaDependency.toCabalDependency)
  }

  private def getEtaMavenDependencies(dependencies: Seq[ModuleID]): Seq[String] = {
    EtaDependency.getAllMavenDependencies(dependencies)
      .map(_.toString())
  }

  private def getProductsClasspath: Def.Initialize[Task[Classpath]] = {
    val selectDeps  = ScopeFilter(inDependencies(ThisProject, includeRoot = false))
    val productJars = ((exportedProductJarsIfMissing in Compile) ?? Nil).all(selectDeps)
    Def.task { productJars.value.flatten }
  }

  private def getEtaPackagesTask: Def.Initialize[Task[Seq[EtaPackage]]] = {
    val selectDeps  = ScopeFilter(inDependencies(ThisProject, includeRoot = false))
    val allPackages = (etaPackage in Eta).?.all(selectDeps)
    Def.task { allPackages.value.flatten }
  }

  private def createCabalTask: Def.Initialize[Task[Cabal]] = Def.task {
    val etaVer = getEtaVersion.value
    val supported = (etaSupported in Eta).value
    val workDir = (baseDirectory in Eta).value
    val projectName = name.value + "-eta"
    val projectVersion = EtaDependency.getPackageVersion(version.value)

    val library = Library(
      name = projectName,
      sourceDirectories = getFilePaths(workDir, (sourceDirectories in EtaLib).value),
      exposedModules = (exposedModules in EtaLib).value,
      buildDependencies = getEtaBuildDependencies((libraryDependencies in EtaLib).value),
      mavenDependencies = getEtaMavenDependencies((libraryDependencies in EtaLib).value),
      gitDependencies = (gitDependencies in EtaLib).value,
      cppOptions = (cppOptions in EtaLib).value,
      ghcOptions = (ghcOptions in EtaLib).value,
      extensions = validateExtensions((extensions in EtaLib).value, supported, etaVer),
      includeDirs = getFilePaths(workDir, (includeDirs in EtaLib).value),
      installIncludes = (installIncludes in EtaLib).value,
      language = validateLanguage((language in EtaLib).value, supported, etaVer)
    )

    val executable = (hsMain in EtaExe).value.map { main =>
      Executable(
        name = projectName + "-exe",
        sourceDirectories = getFilePaths(workDir, (sourceDirectories in EtaExe).value),
        buildDependencies = getEtaBuildDependencies((libraryDependencies in EtaExe).value),
        mavenDependencies = getEtaMavenDependencies((libraryDependencies in EtaExe).value),
        gitDependencies = (gitDependencies in EtaExe).value,
        hsMain = Some(main),
        cppOptions = (cppOptions in EtaExe).value,
        ghcOptions = (ghcOptions in EtaExe).value,
        extensions = validateExtensions((extensions in EtaExe).value, supported, etaVer),
        includeDirs = getFilePaths(workDir, (includeDirs in EtaExe).value),
        installIncludes = (installIncludes in EtaExe).value,
        language = validateLanguage((language in EtaExe).value, supported, etaVer)
      )
    }

    val testSuite = (hsMain in EtaTest).value.map { main =>
      TestSuite(
        name = projectName + "-test",
        sourceDirectories = getFilePaths(workDir, (sourceDirectories in EtaTest).value),
        buildDependencies = getEtaBuildDependencies((libraryDependencies in EtaTest).value),
        mavenDependencies = getEtaMavenDependencies((libraryDependencies in EtaTest).value),
        gitDependencies = (gitDependencies in EtaTest).value,
        hsMain = Some(main),
        cppOptions = (cppOptions in EtaTest).value,
        ghcOptions = (ghcOptions in EtaTest).value,
        extensions = validateExtensions((extensions in EtaTest).value, supported, etaVer),
        includeDirs = getFilePaths(workDir, (includeDirs in EtaTest).value),
        installIncludes = (installIncludes in EtaTest).value,
        language = validateLanguage((language in EtaTest).value, supported, etaVer),
        testSuiteType = (testSuiteType in EtaTest).value
      )
    }

    Cabal(
      projectName = projectName,
      projectVersion = projectVersion,
      projectLibrary = Some(library),
      executables = executable.toList,
      testSuites = testSuite.toList
    )
  }

  private def validateLanguage(language: String, supported: Etlas.Supported, etaVersion: EtaVersion): String = {
    if (supported.languages contains language) language
    else sys.error(s"Language '$language' is not recognized by Eta v${etaVersion.friendlyVersion}")
  }

  private def validateExtensions(extensions: Seq[String], supported: Etlas.Supported, etaVersion: EtaVersion): Seq[String] = {
    extensions.map { extension =>
      if (supported.extensions contains extension) extension
      else sys.error(s"Extension '$extension' is not recognized by Eta v${etaVersion.friendlyVersion}")
    }
  }

  private case class ResolvedCabal(classpath: Classpath)

  private def resolveCabal(etlas: Etlas, cabal: Cabal, workDir: File, log: Logger): ResolvedCabal = {
    val tmpCabal = cabal.getTmpCabal
    val tmpPath = workDir / "tmp"
    IO.createDirectory(tmpPath)
    Cabal.writeCabal(tmpPath, tmpCabal, Nil, log)
    Cabal.writeCabalProject(tmpPath, tmpCabal, Nil, log)
    ResolvedCabal(
      classpath = etlas.changeWorkDir(tmpPath).getClasspath(tmpCabal, log, Artifact.all)
    )
  }

  private def refreshCabalTask: Def.Initialize[Task[Cabal]] = Def.task {
    val log = Logger(streams.value)
    (useLocalCabal in Eta).?.value match {
      case None =>
        log.info("There is not Eta project.")
        Cabal.empty
      case Some(true) =>
        log.info("Flag `useLocalCabal in Eta` set to `true`. Uses local .cabal file in root folder.")
        Cabal.parseCabal((baseDirectory in Eta).value, log)
      case Some(false) =>
        val workDir = (baseDirectory in Eta).value
        val cabal = createCabalTask.value
        val resolved = resolveCabal((etlas in Eta).value, cabal, workDir, log)
        val etaPackages = getEtaPackagesTask.value
        val classesFolder = (classDirectory in Compile).value
        val productsClasspath = getProductsClasspath.value
        val fullClasspath = (productsClasspath ++ resolved.classpath).map(_.data) :+ classesFolder

        Cabal.writeCabal(workDir, cabal, etaPackages, log)
        Cabal.writeCabalProject(workDir, cabal, etaPackages, log)
        Cabal.writeCabalProjectLocal(workDir, cabal, etaPackages, fullClasspath, log)

        cabal
    }
  }

  private def etaInitCommand: Command = Command.command("eta-init") { state =>
    val extracted = Project.extract(state)
    val workDir = extracted.get(baseDirectory in Eta)
    val log = Logger(extracted.get(sLog))
    Cabal.getCabalFile(workDir) match {
      case Some(file) =>
        log.warn(s"Found '$file' in '${workDir.getCanonicalPath}'. Could not initialize new Eta project.")
        state
      case None =>
        extracted.get(etlas in Eta).init(
          extracted.get(normalizedName),
          extracted.get(description),
          EtaDependency.getPackageVersion(extracted.get(version)),
          extracted.get(developers),
          extracted.get(homepage),
          extracted.get(sourceDirectory in EtaLib),
          log
        )
        extracted.appendWithSession(baseProjectSettings, state)
    }
  }

  private def etaReplCommand: Command = Command.command("eta-repl") { state =>
    val extracted = Project.extract(state)
    extracted.get(etlas in Eta).repl(extracted.get(sLog)).get
    println()
    state
  }

  private def etaLanguages: Command = Command.command("eta-languages") { state =>
    val extracted = Project.extract(state)
    extracted.get(etaSupported in Eta).languages.foreach(println)
    state
  }

  private def etaExtensions: Command = Command.command("eta-extensions") { state =>
    val extracted = Project.extract(state)
    extracted.get(etaSupported in Eta).extensions.foreach(println)
    state
  }

}
