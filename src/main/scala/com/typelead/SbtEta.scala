package com.typelead

import sbt.Keys._
import sbt.{Def, _}

object SbtEta extends AutoPlugin {

  import Cabal._

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {

    val Eta: Configuration = config("Eta")
    val EtaLib: Configuration = config("EtaLib")
    val EtaExe: Configuration = config("EtaExe")
    val EtaTest: Configuration = config("EtaTest")

    val etaVersion   = SettingKey[String]("eta-version", "Version of the Eta compiler.")
    val etlasVersion = SettingKey[String]("etlas-version", "Version of the Etlas build tool.")
    val etaCompile   = TaskKey[Unit]("eta-compile", "Build your Eta project.")

    // Eta configuration DSL

    val useLocalCabal = SettingKey[Boolean]("eta-dsl-useLocalCabal", "If `true`, use local .cabal file in root folder. If `false`, recreate .cabal file from project settings.")
    val hsMain = SettingKey[Option[String]]("eta-dsl-hsMain", "Specifies main class for artifact.")
    val exposedModules = SettingKey[Seq[String]]("eta-dsl-exposedModules", "A list of modules added by this package.")
    val language = SettingKey[String]("eta-dsl-language", "Specifies the language to use for the build.")
    val extensions = SettingKey[Seq[String]]("eta-dsl-extensions", "The set of language extensions to enable or disable for the build.")
    val cppOptions = SettingKey[Seq[String]]("eta-dsl-cppOptions", "The flags to send to the preprocessor used by the Eta compiler to preprocess files that enable the CPP extension.")
    val ghcOptions = SettingKey[Seq[String]]("eta-dsl-ghcOptions", "The direct flags to send to the Eta compiler.")
    val includeDirs = SettingKey[Seq[File]]("eta-dsl-includeDirs", "Paths to directories which contain include files that can later be referenced with `#include` directives.")
    val installIncludes = SettingKey[Seq[String]]("eta-dsl-installIncludes", "Names of include files to install along with the package being built.")
    val testSuiteType = SettingKey[Cabal.TestSuiteTypes.Value]("eta-dsl-testSuiteType", "The interface type and version of the test suite.")

    def eta(packageName: String): ModuleID = EtaDependency(packageName)
    def eta(packageName: String, version: String): ModuleID = EtaDependency(packageName, version)

    val Haskell98 = "Haskell98"
    val Haskell2010 = "Haskell2010"

    val exitcodeTestSuite: Cabal.TestSuiteTypes.Value = Cabal.TestSuiteTypes.exitcode
    val detailedTestSuite: Cabal.TestSuiteTypes.Value = Cabal.TestSuiteTypes.detailed

  }

  import autoImport._

  private val etaCabal = TaskKey[Cabal]("eta-cabal", "Structure of the .cabal file.")

  private val baseEtaSettings: Seq[Def.Setting[_]] = {
    Seq(
      baseDirectory in Eta := target.value / "eta",
      target in Eta := target.value / "eta" / "dist",
      // Standard tasks
      clean in Eta := {
        Etlas.clean((baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value))
      },
      run in Eta := {
        Etlas.runArtifacts(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value), Cabal.Artifact.all)
      },
      test in Eta := {
        Etlas.testArtifacts(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value), Cabal.Artifact.all)
      },
      mainClass in Eta := {
        (etaCompile in Compile).value
        etaCabal.value.getMainClass
      },
      projectDependencies in Eta := {
        Etlas.getMavenDependencies(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(sLog.value), Artifact.not(Artifact.testSuite)) ++
        Etlas.getMavenDependencies(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(sLog.value), Artifact.testSuite).map(_ % Test)
      },
      // DSL
      useLocalCabal in Eta := false,
      language in Eta := Haskell2010,
      extensions in Eta := Nil,
      cppOptions in Eta := Nil,
      ghcOptions in Eta := Nil,
      includeDirs in Eta := Nil,
      installIncludes in Eta := Nil,
      testSuiteType in Eta := exitcodeTestSuite,
      libraryDependencies in Eta := Seq(EtaDependency.base)
    ) ++
      makeSettings(EtaLib, Compile, Artifact.library) ++
      makeSettings(EtaExe, Compile, Artifact.executable) ++
      makeSettings(EtaTest,   Test, Artifact.or(Artifact.library, Artifact.testSuite))
  }

  private def makeSettings(config: Configuration, base: Configuration, filter: Cabal.Artifact.Filter): Seq[Def.Setting[_]] = {
    Seq(
      sourceDirectory in config := (sourceDirectory in base).value / "eta",
      sourceDirectories in config := Seq((sourceDirectory in config).value),
      exportedProductJars in config := {
        val log = Logger(streams.value)
        (etaCompile in base).value
        etaCabal.value.getArtifactsJars((target in Eta).value, (etaVersion in Eta).value, filter).flatMap { jar =>
          log.info("Eta artifact JAR: " + jar.getCanonicalPath)
          PathFinder(jar).classpath
        }
      },
      unmanagedClasspath in config := {
        Etlas.getClasspath(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value), filter)
      },
      // DSL
      hsMain in config := None,
      exposedModules in config := Nil,
      language in config := (language in Eta).value,
      extensions in config := (extensions in Eta).value,
      cppOptions in config := (cppOptions in Eta).value,
      ghcOptions in config := (ghcOptions in Eta).value,
      includeDirs in config := (includeDirs in Eta).value,
      installIncludes in config := (installIncludes in Eta).value,
      testSuiteType in config := (testSuiteType in Eta).value,
      libraryDependencies in config := (libraryDependencies in Eta).value
    )
  }

  val baseProjectSettings: Seq[Def.Setting[_]] = baseEtaSettings ++ Seq(

    // Specific Eta tasks

    etaCabal := {
      refreshCabal(Project.extract(state.value), Logger(sLog.value))
    },
    etaVersion := {
      Etlas.etaVersion((baseDirectory in Eta).value, Logger(sLog.value))
    },
    etlasVersion := {
      Etlas.etlasVersion((baseDirectory in Eta).value, Logger(sLog.value))
    },

    etaCompile in Compile := {
      Etlas.build(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value))
    },
    etaCompile in Test := {
      Etlas.buildArtifacts(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value), Artifact.testSuite)
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

    unmanagedJars in Compile ++= (unmanagedClasspath in EtaLib).value,
    unmanagedJars in Compile ++= (exportedProductJars in EtaLib).value,

    unmanagedJars in Compile ++= (unmanagedClasspath in EtaExe).value,
    unmanagedJars in Compile ++= (exportedProductJars in EtaExe).value,

    unmanagedJars in Test ++= (unmanagedClasspath in EtaTest).value,
    unmanagedJars in Test ++= (exportedProductJars in EtaTest).value,

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

    commands ++= Seq(etaInitCommand, etaRefreshCommand, etaReplCommand)
  )

  override def projectSettings: Seq[Def.Setting[_]] = baseProjectSettings
  override def projectConfigurations: Seq[Configuration] = Seq(Eta, EtaLib, EtaExe, EtaTest)

  private def getFilePaths(extracted: Extracted, key: SettingKey[Seq[File]], config: Configuration): Seq[String] = {
    extracted.get(key in config).map { file =>
      IO.relativize(extracted.get(baseDirectory in Eta), file).getOrElse(file.getCanonicalPath)
    }
  }

  private def getEtaBuildDependencies(extracted: Extracted, config: Configuration): Seq[String] = {
    EtaDependency.getAllEtaDependencies(extracted.get(libraryDependencies in config))
      .map(EtaDependency.toCabalDependency)
  }

  private def getEtaMavenDependencies(extracted: Extracted, config: Configuration): Seq[String] = {
    EtaDependency.getAllMavenDependencies(extracted.get(libraryDependencies in config))
      .map(_.toString())
  }

  private def refreshCabal(extracted: Extracted, log: Logger): Cabal = {
    val cwd = extracted.get(baseDirectory in Eta)

    val cabal = if (extracted.get(useLocalCabal in Eta)) {
      log.info("Flag `useLocalCabal in Eta` set to `true`. Uses local .cabal file in root folder.")
      Cabal.parseCabal(cwd, log)
    } else {
      val projectName = extracted.get(name) + "-eta"
      val projectVersion = EtaDependency.getPackageVersion(extracted.get(version))

      val library = Library(
        name = projectName,
        sourceDirectories = getFilePaths(extracted, sourceDirectories, EtaLib),
        exposedModules = extracted.get(exposedModules in EtaLib),
        buildDependencies = getEtaBuildDependencies(extracted, EtaLib),
        mavenDependencies = getEtaMavenDependencies(extracted, EtaLib),
        cppOptions = extracted.get(cppOptions in EtaLib),
        ghcOptions = extracted.get(ghcOptions in EtaLib),
        extensions = extracted.get(extensions in EtaLib),
        includeDirs = getFilePaths(extracted, includeDirs, EtaLib),
        installIncludes = extracted.get(installIncludes in EtaLib),
        language = extracted.get(language in EtaLib)
      )

      val executable = extracted.get(hsMain in EtaExe).map { main =>
        Executable(
          name = projectName + "-exe",
          sourceDirectories = getFilePaths(extracted, sourceDirectories, EtaExe),
          buildDependencies = getEtaBuildDependencies(extracted, EtaExe),
          mavenDependencies = getEtaMavenDependencies(extracted, EtaExe),
          hsMain = Some(main),
          cppOptions = extracted.get(cppOptions in EtaExe),
          ghcOptions = extracted.get(ghcOptions in EtaExe),
          extensions = extracted.get(extensions in EtaExe),
          includeDirs = getFilePaths(extracted, includeDirs, EtaExe),
          installIncludes = extracted.get(installIncludes in EtaExe),
          language = extracted.get(language in EtaExe)
        ).addLibrary(library)
      }

      val testSuite = extracted.get(hsMain in EtaTest).map { main =>
        TestSuite(
          name = projectName + "-test",
          sourceDirectories = getFilePaths(extracted, sourceDirectories, EtaTest),
          buildDependencies = getEtaBuildDependencies(extracted, EtaTest),
          mavenDependencies = getEtaMavenDependencies(extracted, EtaTest),
          hsMain = Some(main),
          cppOptions = extracted.get(cppOptions in EtaTest),
          ghcOptions = extracted.get(ghcOptions in EtaTest),
          extensions = extracted.get(extensions in EtaTest),
          includeDirs = getFilePaths(extracted, includeDirs, EtaTest),
          installIncludes = extracted.get(installIncludes in EtaTest),
          language = extracted.get(language in EtaTest),
          testSuiteType = extracted.get(testSuiteType in EtaTest)
        ).addLibrary(library)
      }

      val cabal = Cabal(
        projectName = projectName,
        projectVersion = projectVersion,
        projectLibrary = Some(library),
        executables = executable.toList,
        testSuites = testSuite.toList
      )

      Cabal.writeCabal(cwd, cabal, log)
    }

    //log.info(cabal.toString)

    cabal
  }

  private def etaInitCommand: Command = Command.command("eta-init") { state =>
    val extracted = Project.extract(state)
    val cwd = extracted.get(baseDirectory in Eta)
    val log = Logger(extracted.get(sLog))
    Cabal.getCabalFile(cwd) match {
      case Some(file) =>
        log.warn(s"Found '$file' in '${cwd.getCanonicalPath}'. Could not initialize new Eta project.")
        state
      case None =>
        Etlas.init(
          cwd,
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

  private def etaRefreshCommand: Command = Command.command("eta-refresh") { state =>
    val extracted = Project.extract(state)
    refreshCabal(extracted, Logger(extracted.get(sLog)))
    state
  }

  private def etaReplCommand: Command = Command.command("eta-repl") { state =>
    val extracted = Project.extract(state)
    Etlas.repl(
      extracted.get(baseDirectory in Eta),
      extracted.get(target in Eta),
      extracted.get(sLog)
    ).get
    println()
    state
  }

}
