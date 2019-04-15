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

    val hsMain = SettingKey[Option[String]]("eta-dsl-hsMain", "Specifies main class for artifact.")

    def eta(packageName: String): ModuleID = EtaDependency(packageName)
    def eta(packageName: String, version: String): ModuleID = EtaDependency(packageName, version)

  }

  import autoImport._

  private val etaCabal = SettingKey[Cabal]("eta-cabal", "Structure of .cabal file.")

  private val baseEtaSettings: Seq[Def.Setting[_]] = {
    Seq(
      baseDirectory in Eta := baseDirectory.value,
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
        Etlas.getMavenDependencies(etaCabal.value, (baseDirectory in Eta).value, Logger(sLog.value), Artifact.not(Artifact.testSuite)) ++
        Etlas.getMavenDependencies(etaCabal.value, (baseDirectory in Eta).value, Logger(sLog.value), Artifact.testSuite).map(_ % Test)
      },
      // DSL
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
        (etaCompile in base).value
        etaCabal.value.getArtifactsJars((target in Eta).value, (etaVersion in Eta).value, filter)
      },
      unmanagedClasspath in config := {
        Etlas.getClasspath(etaCabal.value, (baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value), filter)
      },
      // DSL
      hsMain in config := None,
      libraryDependencies in config := (libraryDependencies in Eta).value
    )
  }

  val baseProjectSettings: Seq[Def.Setting[_]] = baseEtaSettings ++ Seq(

    // Specific Eta tasks

    etaCabal := {
      Cabal.parseCabal((baseDirectory in Eta).value, Logger(sLog.value))
    },
    etaVersion := {
      Etlas.etaVersion((baseDirectory in Eta).value, Logger(sLog.value))
    },
    etlasVersion := {
      Etlas.etlasVersion((baseDirectory in Eta).value, Logger(sLog.value))
    },

    etaCompile in Compile := {
      Etlas.build((baseDirectory in Eta).value, (target in Eta).value, Logger(streams.value))
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

    libraryDependencies in Compile ++= EtaDependency.getAllMavenDependencies((libraryDependencies in EtaLib).value),
    libraryDependencies in Compile ++= EtaDependency.getAllMavenDependencies((libraryDependencies in EtaExe).value),
    libraryDependencies in Test    ++= EtaDependency.getAllMavenDependencies((libraryDependencies in EtaTest).value),

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

  private def getSourceDirectories(extracted: Extracted, config: Configuration): Seq[String] = {
    extracted.get(sourceDirectories in config).flatMap { dir =>
      IO.relativize(extracted.get(baseDirectory in Eta), dir)
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
          extracted.get(version),
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
    val cwd = extracted.get(baseDirectory in Eta)
    val log = Logger(extracted.get(sLog))

    val projectName = extracted.get(name) + "-eta"
    val projectVersion = extracted.get(version)

    val library = Library(
      name = projectName,
      sourceDirectories = getSourceDirectories(extracted, EtaLib),
      exposedModules = Nil,
      buildDependencies = getEtaBuildDependencies(extracted, EtaLib),
      mavenDependencies = getEtaMavenDependencies(extracted, EtaLib),
      ghcOptions = Nil,
      defaultLanguage = Haskell2010
    )

    val executable = extracted.get(hsMain in EtaExe).map { main =>
      Executable(
        name = projectName + "-exe",
        sourceDirectories = getSourceDirectories(extracted, EtaExe),
        exposedModules = Nil,
        buildDependencies = getEtaBuildDependencies(extracted, EtaExe),
        mavenDependencies = getEtaMavenDependencies(extracted, EtaExe),
        hsMain = Some(main),
        ghcOptions = Nil,
        defaultLanguage = Haskell2010
      ).addLibrary(library)
    }

    val testSuite = extracted.get(hsMain in EtaTest).map { main =>
      TestSuite(
        name = projectName + "-test",
        sourceDirectories = getSourceDirectories(extracted, EtaTest),
        exposedModules = Nil,
        buildDependencies = getEtaBuildDependencies(extracted, EtaTest),
        mavenDependencies = getEtaMavenDependencies(extracted, EtaTest),
        hsMain = Some(main),
        ghcOptions = Nil,
        defaultLanguage = Haskell2010
      ).addLibrary(library)
    }

    val cabal = Cabal(
      projectName    = projectName,
      projectVersion = projectVersion,
      projectLibrary = Some(library),
      executables    = executable.toList,
      testSuites     = testSuite.toList
    )

    Cabal.writeCabal(extracted.get(target in Eta), cabal)

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
