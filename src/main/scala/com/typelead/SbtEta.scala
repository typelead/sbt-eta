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

    val etaSource  = SettingKey[File]("eta-source", "Default Eta source directory.")
    val etaTarget  = SettingKey[File]("eta-target", "Location to store build artifacts.")
  }

  import autoImport._

  val baseEtaSettings: Seq[Def.Setting[_]] = Seq(
    etaTarget := target.value / "eta" / "dist",

    etaCompile in Compile := {
      val s   = streams.value
      val cwd = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      Etlas.build(cwd, dist, Logger(s))
    },

    etaCompile in Test := {
      val s   = streams.value
      val cwd = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      Etlas.buildArtifacts(cwd, dist, Logger(s), Artifact.testSuite)
    },

    etaSource in Compile := (sourceDirectory in Compile).value / "eta",

    etaClean := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      Etlas.clean(cwd, dist, Logger(s))
    },

    etaRun := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      Etlas.run(cwd, dist, Logger(s))
    },

    etaTest := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      Etlas.test(cwd, dist, Logger(s))
    },

    clean := {
      etaClean.value
      clean.value
    },

    libraryDependencies := {
      val s    = sLog.value
      val deps = libraryDependencies.value
      val cwd  = (etaSource in Compile).value

      Etlas.install(cwd, Logger(s))

      deps ++
        Etlas.getLibraryDependencies(cwd, Logger(s), Artifact.not(Artifact.testSuite)) ++
        Etlas.getLibraryDependencies(cwd, Logger(s), Artifact.testSuite).map(_ % Test)
    },

    unmanagedJars in Compile := {
      (libraryDependencies in Compile).value
      (etaCompile in Compile).value

      val s    = streams.value
      val cp   = (unmanagedJars in Compile).value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath

      cp ++ Etlas.getFullClasspath(cwd, dist, Logger(s), Artifact.not(Artifact.testSuite))
    },
    unmanagedJars in Test := {
      (libraryDependencies in Compile).value
      (etaCompile in Test).value

      val s    = streams.value
      val cp   = (unmanagedJars in Test).value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath

      cp ++ Etlas.getFullClasspath(cwd, dist, Logger(s), Artifact.testSuite)
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
      getMainClass((etaSource in Compile).value, (mainClass in (Compile, run)).value, Logger(streams.value))
    },
    mainClass in (Compile, packageBin) := {
      getMainClass((etaSource in Compile).value, (mainClass in (Compile, packageBin)).value, Logger(streams.value))
    },

    watchSources ++= ((etaSource in Compile).value ** "*").get,
  )

  override def projectSettings: Seq[Def.Setting[_]] = baseEtaSettings

}
