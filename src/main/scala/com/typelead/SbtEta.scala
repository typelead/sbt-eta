package com.typelead

import sbt._
import Keys._
import scala.collection.mutable.ArrayBuffer

object SbtEta extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val etaCompile = TaskKey[Unit]("eta-compile", "Build your Eta project.")
    val etaRun     = TaskKey[Unit]("eta-run", "Clean your Eta project.")
    val etaClean   = TaskKey[Unit]("eta-clean", "Clean your Eta project.")

    val etaSource  = SettingKey[File]("eta-source", "Default Eta source directory.")
    val etaTarget  = SettingKey[File]("eta-target", "Location to store build artifacts.")
  }

  import autoImport._

  val baseEtaSettings = Seq(
    etaTarget := target.value / "eta" / "dist",
    etaCompile in Compile := {
      val s   = streams.value
      val cwd = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath;
      etlas(Seq("build", "--builddir", dist), cwd, Left(s))
      ()
    },
    etaSource in Compile := (sourceDirectory in Compile).value / "eta",
    etaClean := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath;
      etlas(Seq("clean", "--builddir", dist), cwd, Left(s))
      ()
    },
    etaRun := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath;
      etlas(Seq("run", "--builddir", dist), cwd, Left(s))
      ()
    },
    clean := {
      etaClean.value
      clean.value
    },
    libraryDependencies := {
      val s    = sLog.value
      val deps = libraryDependencies.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath;
      s.info("[etlas] Checking Maven dependencies...")
      val output = etlas(Seq("deps", "--maven", "--builddir", dist), cwd, Right(s), true,
                         s => !(s.r.findAllIn(":").length == 2 && !s.contains(" ")))
      deps ++ parseMavenDeps(output)
    },
    unmanagedJars in Compile := {
      (libraryDependencies in Compile).value
      (etaCompile in Compile).value
      val s    = streams.value
      val cp   = (unmanagedJars in Compile).value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath;
      s.log.info("[etlas] Retrieving Eta dependency jar paths...")
      val filterFn = (s:String) => !(s.contains(".jar") && !(s.contains("Linking")))
      val output = etlas(Seq("deps", "--classpath", "--builddir", dist), cwd, Left(s),
                         true, filterFn)
      val etaCp  = output.filter(s => !filterFn(s))
                         .map(s => PathFinder(file(s)))
                         .fold(PathFinder.empty)((s1, s2) => s1 +++ s2)
      cp ++ etaCp.classpath
    },
    watchSources := watchSources.value ++ ((etaSource in Compile).value ** "*").get
  )

  override def projectSettings = baseEtaSettings

  def etlas(args: Seq[String], cwd: File, streams: Either[TaskStreams, Logger]
           ,saveOutput: Boolean = false
           ,filterLog: String => Boolean = s => true): Seq[String] = {
    val lineBuffer = new ArrayBuffer[String]
    val logInfo    = streams match {
      case Left(out)  => (s: String) => out.log.info(s);
      case Right(out) => (s: String) => out.info(s);
    }
    val logError   = streams match {
      case Left(out)  => (s: String) => out.log.error(s);
      case Right(out) => (s: String) => out.error(s);
    }
    val logger =
      new ProcessLogger {
        override def info(s: => String) = {
          lineBuffer += s
          if (filterLog(s)) {
            logInfo("[etlas] " ++ s)
          }
        }
        override def error(s: => String) = {
          lineBuffer += s
          if (filterLog(s)) {
            logError("[etlas] " ++ s)
          }
        }
        override def buffer[T](s: => T) = s
      }
    val exitCode = Process("etlas" +: args, cwd) ! logger
    if (exitCode != 0) {
      var errorString = "\n";
      errorString += "\n [etlas] Exit Failure " ++ exitCode.toString;
      sys.error(errorString)
    }
    if (saveOutput) lineBuffer
    else Nil
  }

  def parseMavenDeps(lines: Seq[String]): Seq[ModuleID] =
    lines.filter(line => line.r.findAllIn(":").length == 2)
         .map(line => {
                 val parts = line.split(":")
                 parts(0) % parts(1) % parts(2)
               })

}
