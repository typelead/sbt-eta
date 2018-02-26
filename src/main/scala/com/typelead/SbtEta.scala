package com.typelead

import sbt._
import Keys._
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.sys.process.{Process, ProcessLogger}

object SbtEta extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val etaCompile = TaskKey[Unit]("eta-compile", "Build your Eta project.")
    val etaRun     = TaskKey[Unit]("eta-run", "Run your Eta project.")
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
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("build", "--builddir", dist), cwd, Left(s))
      ()
    },
    etaSource in Compile := (sourceDirectory in Compile).value / "eta",
    etaClean := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("clean", "--builddir", dist), cwd, Left(s))
      ()
    },
    etaRun := {
      val s    = streams.value
      val cwd  = (etaSource in Compile).value
      val dist = etaTarget.value.getCanonicalPath
      etlas(Seq("run", "--builddir", dist), cwd, Left(s))
      ()
    },
    clean := {
      etaClean.value
      clean.value
    },
    libraryDependencies := {
      val s    = sLog.value
      var deps = libraryDependencies.value
      val cwd  = (etaSource in Compile).value
      s.info("[etlas] Installing dependencies...")
      etlas(Seq("install", "--dependencies-only"), cwd, Right(s))
      s.info("[etlas] Checking Maven dependencies...")
      getCabalFile(cwd) match {
        case Some(cabal) => getLibName(cwd, cabal) match {
          case Some(lib) => {
            val filterFn = (s:String) => !(s.r.findAllIn(":").length == 2 && !s.contains(" "))
            val output = etlas(Seq("deps", "--maven", "lib:" ++ lib),
                               cwd, Right(s), true, filterFn)
            deps = deps ++ parseMavenDeps(output)
          }
          case None => s.error("[etlas] No project name specified.")
        }
        case None => s.error("[etlas] No cabal file found.")
      }
      deps
    },
    unmanagedJars in Compile := {
      (libraryDependencies in Compile).value
      (etaCompile in Compile).value
      val s    = streams.value
      var cp   = (unmanagedJars in Compile).value
      val cwd  = (etaSource in Compile).value
      s.log.info("[etlas] Installing dependencies...")
      etlas(Seq("install", "--dependencies-only"), cwd, Left(s))
      s.log.info("[etlas] Retrieving Eta dependency jar paths...")
      getCabalFile(cwd) match {
        case Some(cabal) => getLibName(cwd, cabal) match {
          case Some(lib) => {
            val filterFn = (s:String) => !(s.contains(".jar") && !(s.contains("Linking")))
            val output = etlas(Seq("deps", "--classpath", "lib:" ++ lib),
                               cwd, Left(s), true, filterFn)
            val etaCp  = output.filter(s => !filterFn(s))
                               .map(s => PathFinder(file(s)))
                               .fold(PathFinder.empty)((s1, s2) => s1 +++ s2)
            cp = cp ++ etaCp.classpath
          }
          case None => s.log.error("[etlas] No project name specified.")
        }
        case None => s.log.error("[etlas] No cabal file found.")
      }
      cp
    },
    watchSources ++= ((etaSource in Compile).value ** "*").get
  )

  override def projectSettings = baseEtaSettings

  def etlas(args: Seq[String], cwd: File, streams: Either[TaskStreams, Logger]
           ,saveOutput: Boolean = false
           ,filterLog: String => Boolean = s => true): Seq[String] = {
    val lineBuffer = new ArrayBuffer[String]
    val logInfo    = streams match {
      case Left(out)  => (s: String) => out.log.info(s)
      case Right(out) => (s: String) => out.info(s)
    }
    val logError   = streams match {
      case Left(out)  => (s: String) => out.log.error(s)
      case Right(out) => (s: String) => out.error(s)
    }
    val logger =
      new ProcessLogger {
        override def out(s: => String) = {
          lineBuffer += s
          if (filterLog(s)) {
            logInfo("[etlas] " ++ s)
          }
        }
        override def err(s: => String) = {
          lineBuffer += s
          if (filterLog(s)) {
            logError("[etlas] " ++ s)
          }
        }
        override def buffer[T](s: => T) = s
      }
    val exitCode = Process("etlas" +: args, cwd) ! logger
    if (exitCode != 0) {
      var errorString = "\n"
      errorString += "\n [etlas] Exit Failure " ++ exitCode.toString
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

  def getCabalFile(cwd: File): Option[String] = {
    cwd.listFiles
      .map(_.getName)
      .filter(_.matches(""".*\.cabal$"""))
      .headOption
  }

  def getLibName(cwd: File, cabal: String): Option[String] = {
    Source.fromFile(cwd / cabal).getLines
      .filter(_.matches("""\s*name:\s*\S+\s*$"""))
      .toSeq
      .headOption
      .map(_.split(":")(1))
      .map(_.trim)
  }

}
