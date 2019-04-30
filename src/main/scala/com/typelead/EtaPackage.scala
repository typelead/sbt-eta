package com.typelead

import sbt.Keys._
import sbt._

final case class EtaPackage(cabal: Cabal, jars: Seq[File], packageDb: File, dependencies: Map[String, Seq[EtaPackage.Dependency]]) {

  import EtaPackage._

  val name: String = cabal.projectName
  val version: String = cabal.projectVersion

  def getAllDependencies(filter: Cabal.Artifact.Filter): Seq[Dependency] = {
    val artifacts = cabal.getArtifacts(filter).map(_.name).toSet
    dependencies.filterKeys(artifacts).values.flatten.toList
  }

  def getAllMavenDependencies(filter: Cabal.Artifact.Filter): Seq[ModuleID] = getAllDependencies(filter).collect {
    case MavenDependency(moduleId) => Seq(moduleId)
    case LibraryDependency(_, mavenDeps, _, _) => mavenDeps
  }.flatten

  def getAllLibraryJars(filter: Cabal.Artifact.Filter): Seq[File] = getAllDependencies(filter).collect {
    case LibraryDependency(_, _, libraryJars, _) => libraryJars
  }.flatten

  def getClasspath(filter: Cabal.Artifact.Filter): Classpath = PathFinder(getAllLibraryJars(filter)).classpath

}

object EtaPackage {

  sealed trait Dependency
  final case class MavenDependency(moduleId: ModuleID) extends Dependency
  final case class LibraryDependency(name: String, mavenDeps: Seq[ModuleID], libraryJars: Seq[File], dependencies: Seq[String]) extends Dependency

}
