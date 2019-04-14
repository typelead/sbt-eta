package com.typelead

import sbt.Keys._
import sbt.{Def, _}

object SbtEta extends AutoPlugin {

  import Cabal._

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val etaCompile = TaskKey[Unit]("eta-compile", "Build your Eta project.")
    val etaRun     = TaskKey[Unit]("eta-run"    , "Run your Eta project.")
    val etaTest    = TaskKey[Unit]("eta-test"   , "Run your Eta project's tests.")
    val etaClean   = TaskKey[Unit]("eta-clean"  , "Clean your Eta project.")

    val etaPackageDir = SettingKey[File]("eta-package-dir", "Root directory of the Eta package (default = baseDirectory).")
    val etaSource     = SettingKey[File]("eta-source"     , "Default Eta source directory (default = src/main/eta).")
    val etaTarget     = SettingKey[File]("eta-target"     , "Location to store build artifacts (default = target/eta/dist).")
  }

  import autoImport._

  val baseEtaSettings: Seq[Def.Setting[_]] = Seq(

    etaPackageDir := baseDirectory.value,
    etaSource in Compile := (sourceDirectory in Compile).value / "eta",
    etaTarget := target.value / "eta" / "dist",

    etaCompile in Compile := {
      Etlas.build(etaPackageDir.value, etaTarget.value, Logger(streams.value))
    },

    etaCompile in Test := {
      Etlas.buildArtifacts(etaPackageDir.value, etaTarget.value, Logger(streams.value), Artifact.testSuite)
    },

    etaClean := {
      Etlas.clean(etaPackageDir.value, etaTarget.value, Logger(streams.value))
    },

    etaRun := {
      Etlas.run(etaPackageDir.value, etaTarget.value, Logger(streams.value))
    },

    etaTest := {
      Etlas.testArtifacts(etaPackageDir.value, etaTarget.value, Logger(streams.value))
    },

    clean := {
      etaClean.value
      clean.value
    },

    libraryDependencies := {
      val deps = libraryDependencies.value

      //Etlas.install(etaPackageDir.value, Logger(sLog.value))

      deps ++
        Etlas.getLibraryDependencies(etaPackageDir.value, Logger(sLog.value), Artifact.not(Artifact.testSuite)) ++
        Etlas.getLibraryDependencies(etaPackageDir.value, Logger(sLog.value), Artifact.testSuite).map(_ % Test)
    },

    unmanagedJars in Compile := {
      (libraryDependencies in Compile).value
      (etaCompile in Compile).value

      val cp = (unmanagedJars in Compile).value

      cp ++ Etlas.getFullClasspath(etaPackageDir.value, etaTarget.value, Logger(streams.value), Artifact.not(Artifact.testSuite))
    },
    unmanagedJars in Test := {
      (libraryDependencies in Compile).value
      (etaCompile in Test).value

      val cp = (unmanagedJars in Test).value

      cp ++ Etlas.getFullClasspath(etaPackageDir.value, etaTarget.value, Logger(streams.value), Artifact.testSuite)
    },

    compile in Test := {
      (etaCompile in Test).value
      (compile in Test).value
    },
    test in Test := {
      etaTest.value
      (test in Test).value
    },

    mainClass in (Compile, run) := {
      getMainClass(etaPackageDir.value, (mainClass in (Compile, run)).value, Logger(streams.value))
    },
    mainClass in (Compile, packageBin) := {
      getMainClass(etaPackageDir.value, (mainClass in (Compile, packageBin)).value, Logger(streams.value))
    },

    watchSources ++= ((etaSource in Compile).value ** "*").get,

    commands += etaInitCommand
  )

  override def projectSettings: Seq[Def.Setting[_]] = baseEtaSettings

  private def etaInitCommand: Command = Command.command("eta-init") { state =>
    val extracted = Project.extract(state)
    val cwd = extracted.get(etaPackageDir)
    val log = Logger(extracted.get(sLog))
    Cabal.getCabalFile(cwd) match {
      case Some(cabal) =>
        log.warn(s"Found '$cabal' in '${cwd.getCanonicalPath}'. Could not initialize new Eta project.")
        state
      case None =>
        Etlas.init(
          cwd,
          extracted.get(normalizedName),
          extracted.get(description),
          extracted.get(version),
          extracted.get(developers),
          extracted.get(homepage),
          extracted.get(etaSource in Compile),
          log
        )
        extracted.appendWithSession(baseEtaSettings, state)
    }
  }

}
