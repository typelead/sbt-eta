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
      }
    ) ++
      makeSettings(EtaLib, Compile, Artifact.library) ++
      makeSettings(EtaExe, Compile, Artifact.executable) ++
      makeSettings(EtaTest, Test, Artifact.or(Artifact.library, Artifact.testSuite))
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
      }
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

    projectDependencies := {
      projectDependencies.value ++ (projectDependencies in Eta).value
    },

    unmanagedJars in Compile := {
      (unmanagedJars in Compile).value ++
        (unmanagedClasspath in EtaLib).value ++
        (exportedProductJars in EtaLib).value
    },
    unmanagedJars in Test := {
      (unmanagedJars in Test).value ++
        (unmanagedClasspath in EtaTest).value ++
        (exportedProductJars in EtaTest).value
    },

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

    watchSources ++= {
      ((sourceDirectory in EtaLib).value ** "*") +++
      ((sourceDirectory in EtaExe).value ** "*") +++
      ((sourceDirectory in EtaTest).value ** "*")
    }.get,

    commands ++= Seq(etaInitCommand, etaReplCommand)
  )

  override def projectSettings: Seq[Def.Setting[_]] = baseProjectSettings
  override def projectConfigurations: Seq[Configuration] = Seq(Eta, EtaLib, EtaExe, EtaTest)

  private def getSourceDirectories(extracted: Extracted, config: Configuration): Seq[String] = {
    extracted.get(sourceDirectories in config).flatMap { dir =>
      IO.relativize(extracted.get(baseDirectory in Eta), dir)
    }
  }

  private def etaInitCommand: Command = Command.command("eta-init") { state =>
    val extracted = Project.extract(state)
    val cwd = extracted.get(baseDirectory in Eta)
    val log = Logger(extracted.get(sLog))

    val cabal = extracted.get(etaCabal)
    Cabal.writeCabal(
      extracted.get(target in Eta),
      cabal.copy(
        projectName    = extracted.get(name) + "-eta",
        projectVersion = extracted.get(version),
        projectLibrary = cabal.projectLibrary.map(_.addSourceDirectories(getSourceDirectories(extracted, EtaLib))),
        executables    = cabal.executables.map(_.addSourceDirectories(getSourceDirectories(extracted, EtaExe))),
        testSuites     = cabal.testSuites.map(_.addSourceDirectories(getSourceDirectories(extracted, EtaTest)))
      )
    )

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
