package com.typelead

import sbt.Keys._
import sbt.{Def, _}
import EtaDependency.EtaVersion

object SbtEta extends AutoPlugin {

  import Cabal._
  import JsonFormats._

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
    lazy val modules = settingKey[Seq[Module]]("A list of modules added by this package.")
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

    def exposed(moduleName: String): Module = ExposedModule(moduleName)
    def module (moduleName: String): Module = OtherModule  (moduleName)

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
        Etlas.etlasVersion(None, baseDirectory.value, etaSendMetrics.value, Logger(sLog.value))
      },
      etaVersion := {
        Etlas.etaVersion(getEtlasInstallPath.value, baseDirectory.value, etaSendMetrics.value, Logger(sLog.value)).friendlyVersion
      },
      etaSupported := {
        Etlas.etaSupported(getEtlasInstallPath.value, baseDirectory.value, getEtaVersion.value, etaSendMetrics.value, Logger(sLog.value))
      }
    ))
  }

  private lazy val baseEtaSettings: Seq[Def.Setting[_]] = {
    inConfig(Eta)(Seq(
      baseDirectory := (target in Compile).value / "eta",
      target := (target in Compile).value / "eta" / "dist",
      // Plugin specific tasks
      etlas := getEtlas.value,
      etaCabal := refreshCabalTask.value,
      etaPackage := getEtaPackageTask.value,
      //etaPackage := {
      //  etlas.value.getEtaPackage(etaCabal.value, Logger(streams.value))
      //},
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
        val _ = (etaCompile in Compile).value
        etaCabal.value.getMainClass
      },
      projectDependencies := {
        val pack = etaPackage.value
        pack.getAllMavenDependencies(Artifact.not(Artifact.testSuite)) ++
        pack.getAllMavenDependencies(Artifact.testSuite).map(_ % Test)
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
      resolvers := Seq(DefaultMavenRepository),
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
      exportedProducts := {
        val log = Logger(streams.value)
        val _ = (etaCompile in base).value
        (etaCabal in Eta).value.getArtifactsJars((target in Eta).value, getEtaVersion.value, filter).flatMap { jar =>
          log.info("Eta artifact JAR: " + jar.getCanonicalPath)
          PathFinder(jar).classpath
        }
      },
      managedClasspath := {
        (etaPackage in Eta).value.getClasspath(filter)
      },
      // DSL
      hsMain := None,
      modules := Nil,
      language := (language in Eta).value,
      extensions := (extensions in Eta).value,
      cppOptions := (cppOptions in Eta).value,
      ghcOptions := (ghcOptions in Eta).value,
      includeDirs := (includeDirs in Eta).value,
      installIncludes := (installIncludes in Eta).value,
      testSuiteType := (testSuiteType in Eta).value,
      libraryDependencies := (libraryDependencies in Eta).value,
      resolvers := (resolvers in Eta).value,
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
    resolvers ++= (resolvers in EtaLib).value ++ (resolvers in EtaExe).value ++ (resolvers in EtaTest).value,

    unmanagedJars in Compile ++= (managedClasspath in EtaLib).value,
    unmanagedJars in Compile ++= (exportedProducts in EtaLib).value,
    exportedProducts in Compile ++= (exportedProducts in EtaLib).value,

    unmanagedJars in Runtime ++= (managedClasspath in EtaExe).value,
    unmanagedJars in Runtime ++= (exportedProducts in EtaExe).value,
    exportedProducts in Runtime ++= (exportedProducts in EtaExe).value,

    unmanagedJars in Test ++= (managedClasspath in EtaTest).value,
    unmanagedJars in Test ++= (exportedProducts in EtaTest).value,
    exportedProducts in Test ++= (exportedProducts in EtaTest).value,

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

    watchSources ++= ((sourceDirectory in EtaLib ).value ** "*").get,
    watchSources ++= ((sourceDirectory in EtaExe ).value ** "*").get,
    watchSources ++= ((sourceDirectory in EtaTest).value ** "*").get,

    commands ++= Seq(etaReplCommand, etaLanguages, etaExtensions)
  )

  private def getEtaVersion: Def.Initialize[EtaVersion] = Def.setting {
    EtaVersion((etaVersion in ThisBuild).value)
  }

  private def getEtlasInstallPath: Def.Initialize[Option[File]] = Def.setting {
    if ((etlasUseLocal in ThisBuild).value) {
      None
    } else {
      val etlasVer = (etlasVersion in ThisBuild).value
      val etlasRepo = (etlasRepository in ThisBuild).value
      val installPath = (etlasPath in ThisBuild).value
      val sendMetricsFlag = (etaSendMetrics in ThisBuild).value
      val log = Logger(sLog.value)
      synchronized {
        Etlas.download(etlasRepo, installPath, etlasVer, sendMetricsFlag, log)
      }
      Some(installPath)
    }
  }

  private def getEtlas: Def.Initialize[Etlas] = Def.setting {
    Etlas(
      getEtlasInstallPath.value,
      baseDirectory.value,
      target.value,
      getEtaVersion.value,
      (etaSendMetrics in ThisBuild).value
    )
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

  private def getMavenRepositories(resolvers: Seq[Resolver]): Seq[String] = {
    resolvers.collect {
      case DefaultMavenRepository => "central"
      case JCenterRepository      => "jcenter"
      case m: MavenRepository     => m.root
    }
  }

  private def getDepsProductsClasspath: Def.Initialize[Task[Classpath]] = {
    val selectDeps  = ScopeFilter(inDependencies(ThisProject, includeRoot = false))
    val productJars = ((exportedProductsIfMissing in Compile) ?? Nil).all(selectDeps)
    Def.task { productJars.value.flatten }
  }

  private def getDepsEtaPackagesTask: Def.Initialize[Task[Seq[EtaPackage]]] = {
    val selectDeps  = ScopeFilter(inDependencies(ThisProject, includeRoot = false))
    val allPackages = (etaPackage in Eta).?.all(selectDeps)
    Def.task { allPackages.value.flatten }
  }

  private def createCabalTask: Def.Initialize[Task[Cabal]] = Def.task {
    val etaVer = getEtaVersion.value
    val supported = (etaSupported in ThisBuild).value
    val workDir = (baseDirectory in Eta).value
    val projectName = name.value + "-eta"
    val projectVersion = EtaDependency.getPackageVersion(version.value)

    val library = Library(
      name = projectName,
      sourceDirectories = getFilePaths(workDir, (sourceDirectories in EtaLib).value),
      modules = (modules in EtaLib).value,
      buildDependencies = getEtaBuildDependencies((libraryDependencies in EtaLib).value),
      mavenDependencies = getEtaMavenDependencies((libraryDependencies in EtaLib).value),
      mavenRepositories = getMavenRepositories((resolvers in EtaLib).value),
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
        modules = (modules in EtaExe).value,
        buildDependencies = getEtaBuildDependencies((libraryDependencies in EtaExe).value),
        mavenDependencies = getEtaMavenDependencies((libraryDependencies in EtaExe).value),
        mavenRepositories = getMavenRepositories((resolvers in EtaExe).value),
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
        modules = (modules in EtaTest).value,
        buildDependencies = getEtaBuildDependencies((libraryDependencies in EtaTest).value),
        mavenDependencies = getEtaMavenDependencies((libraryDependencies in EtaTest).value),
        mavenRepositories = getMavenRepositories((resolvers in EtaTest).value),
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

  private def refreshCabalTask: Def.Initialize[Task[Cabal]] = Def.task {
    val s = streams.value
    val log = Logger(s)
    (useLocalCabal in Eta).?.value match {
      case None =>
        log.info("There is not Eta project.")
        Cabal.empty
      case Some(true) =>
        log.info("Flag `useLocalCabal in Eta` set to `true`. Uses local .cabal file in root folder.")
        Cabal.parseCabal((baseDirectory in Eta).value, log)
      case Some(false) =>
        val workDir = (baseDirectory in Eta).value
        val cacheFile = s.cacheDirectory / updateCacheName.value
        val cabal = createCabalTask.value
        val etaPackages = getDepsEtaPackagesTask.value
        val resolved = resolveCabal((etlas in Eta).value, cabal, etaPackages, workDir, cacheFile, log)
        val classesFolder = (classDirectory in Compile).value
        val productsClasspath = getDepsProductsClasspath.value
        val fullClasspath = (productsClasspath.map(_.data) ++ resolved.classpath) :+ classesFolder

        IO.delete((workDir * "cabal.*").get)
        Cabal.writeCabal(workDir, cabal, etaPackages, log)
        Cabal.writeCabalProject(workDir, cabal, etaPackages, log)
        Cabal.writeCabalProjectLocal(workDir, cabal, etaPackages, fullClasspath, log)
        resolved.freezeFile.foreach(IO.copyFile(_, workDir / CABAL_PROJECT_FREEZE))

        cabal
    }
  }

  private def resolveCabal(etlas: Etlas, cabal: Cabal, etaPackages: Seq[EtaPackage], workDir: File, cacheFile: File, log: Logger): Cabal.Resolved = {
    val tmpCabal = cabal.getTmpCabal(etaPackages)
    val tmpPath = workDir / "tmp"
    IO.createDirectory(tmpPath)
    IO.delete((tmpPath ** "*").get)
    Cabal.writeCabal(tmpPath, tmpCabal, Nil, log)
    Cabal.writeCabalProject(tmpPath, tmpCabal, Nil, log)
    val freezeFile = etlas.changeWorkDir(tmpPath).freeze(log)
    def doResolve: Cabal.Resolved = {
      Cabal.Resolved(
        classpath  = etlas.changeWorkDir(tmpPath).getEtaPackage(tmpCabal, log).getAllLibraryJars(Artifact.all),
        freezeFile = freezeFile
      )
    }
    val f = SbtUtils.anyFileChanged(cacheFile / "input_eta_files", cacheFile / "output_eta_resolved")(doResolve)
    f(Cabal.trackedFiles(workDir, cabal))
  }

  private def getEtaPackageTask: Def.Initialize[Task[EtaPackage]] = Def.task {
    val s = streams.value
    val cacheFile = s.cacheDirectory / updateCacheName.value
    val cabal = (etaCabal in Eta).value
    val workDir = (baseDirectory in Eta).value
    def makeEtaPackage: EtaPackage = {
      (etlas in Eta).value.getEtaPackage(cabal, Logger(s))
    }
    val f = SbtUtils.anyFileChanged(cacheFile / "input_eta_files", cacheFile / "output_eta_package")(makeEtaPackage)
    f(Cabal.trackedFiles(workDir, cabal))
  }

  private def etaReplCommand: Command = Command.command("eta-repl") { state =>
    val extracted = Project.extract(state)
    extracted.get(etlas in Eta).repl(extracted.get(sLog)).get
    println()
    state
  }

  private def etaLanguages: Command = Command.command("eta-languages") { state =>
    val extracted = Project.extract(state)
    extracted.get(etaSupported in ThisBuild).languages.foreach(println)
    state
  }

  private def etaExtensions: Command = Command.command("eta-extensions") { state =>
    val extracted = Project.extract(state)
    extracted.get(etaSupported in ThisBuild).extensions.foreach(println)
    state
  }

}
