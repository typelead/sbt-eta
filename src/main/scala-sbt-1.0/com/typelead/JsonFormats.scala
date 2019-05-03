package com.typelead

import sbt._
import sjsonnew._

import scala.reflect.ClassTag

trait JsonFormats extends sbt.librarymanagement.LibraryManagementCodec {

  implicit val DependencyJsonFormat: JsonFormat[EtaPackage.Dependency] = new JsonFormat[EtaPackage.Dependency] {
    val MavenDependencyInst = "MavenDependency"
    val LibraryDependencyInst = "LibraryDependency"
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): EtaPackage.Dependency = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          unbuilder.readField[String]("instance") match {
            case MavenDependencyInst =>
              val moduleId = unbuilder.readField[ModuleID]("moduleId")
              unbuilder.endObject()
              EtaPackage.MavenDependency(moduleId)
            case LibraryDependencyInst =>
              val name = unbuilder.readField[String]("name")
              val mavenDeps = unbuilder.readField[Seq[ModuleID]]("mavenDeps")
              val libraryJars = unbuilder.readField[Seq[File]]("libraryJars")
              val dependencies = unbuilder.readField[Seq[String]]("dependencies")
              unbuilder.endObject()
              EtaPackage.LibraryDependency(name, mavenDeps, libraryJars, dependencies)
            case other =>
              deserializationError(s"Invalid `instance` field value ('$other').")
          }
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: EtaPackage.Dependency, builder: Builder[J]): Unit = {
      builder.beginObject()
      obj match {
        case EtaPackage.MavenDependency(moduleId) =>
          builder.addField("instance", MavenDependencyInst)
          builder.addField("moduleId", moduleId)
        case EtaPackage.LibraryDependency(name, mavenDeps, libraryJars, dependencies) =>
          builder.addField("instance", LibraryDependencyInst)
          builder.addField("name", name)
          builder.addField("mavenDeps", mavenDeps)
          builder.addField("libraryJars", libraryJars)
          builder.addField("dependencies", dependencies)
      }
      builder.endObject()
    }
  }

  implicit val EtaPackageJsonFormat: JsonFormat[EtaPackage] = new JsonFormat[EtaPackage] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): EtaPackage = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val cabal = unbuilder.readField[Cabal]("cabal")
          val jars = unbuilder.readField[Seq[File]]("jars")
          val packageDb = unbuilder.readField[File]("packageDb")
          val dependencies = unbuilder.readField[Map[String, Seq[EtaPackage.Dependency]]]("dependencies")
          unbuilder.endObject()
          EtaPackage(cabal, jars, packageDb, dependencies)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }

    override def write[J](obj: EtaPackage, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("cabal", obj.cabal)
      builder.addField("jars", obj.jars)
      builder.addField("packageDb", obj.packageDb)
      builder.addField("dependencies", obj.dependencies)
      builder.endObject()
    }
  }

  implicit val ResolverJsonFormat: JsonFormat[GitDependency.Resolver] = new JsonFormat[GitDependency.Resolver] {
    val BranchInst = "Branch"
    val CommitInst = "Commit"
    val TagInst = "Tag"
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): GitDependency.Resolver = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          unbuilder.readField[String]("instance") match {
            case BranchInst =>
              val branch = unbuilder.readField[String]("branch")
              unbuilder.endObject()
              GitDependency.Branch(branch)
            case CommitInst =>
              val commit = unbuilder.readField[String]("commit")
              unbuilder.endObject()
              GitDependency.Commit(commit)
            case TagInst =>
              val tag = unbuilder.readField[String]("tag")
              unbuilder.endObject()
              GitDependency.Tag(tag)
            case other =>
              deserializationError(s"Invalid `instance` field value ('$other').")
          }
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: GitDependency.Resolver, builder: Builder[J]): Unit = {
      builder.beginObject()
      obj match {
        case GitDependency.Branch(branch) =>
          builder.addField("instance", BranchInst)
          builder.addField("branch", branch)
          builder.endObject()
        case GitDependency.Commit(commit) =>
          builder.addField("instance", CommitInst)
          builder.addField("commit", commit)
          builder.endObject()
        case GitDependency.Tag(tag) =>
          builder.addField("instance", TagInst)
          builder.addField("tag", tag)
          builder.endObject()
      }
    }
  }

  implicit val GitDependencyJsonFormat: JsonFormat[GitDependency] = new JsonFormat[GitDependency] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): GitDependency = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val packageName = unbuilder.readField[String]("packageName")
          val location = unbuilder.readField[String]("location")
          val resolver = unbuilder.readField[GitDependency.Resolver]("resolver")
          val subDir = unbuilder.readField[Option[String]]("subDir")
          unbuilder.endObject()
          GitDependency(packageName, location, resolver, subDir)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: GitDependency, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("packageName", obj.packageName)
      builder.addField("location", obj.location)
      builder.addField("resolver", obj.resolver)
      builder.addField("subDir", obj.subDir)
      builder.endObject()
    }
  }

  implicit val ModuleJsonFormat: JsonFormat[Cabal.Module] = new JsonFormat[Cabal.Module] {
    val OtherModuleInst = "OtherModule"
    val ExposedModuleInst = "ExposedModule"
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Cabal.Module = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val instance = unbuilder.readField[String]("instance")
          val name = unbuilder.readField[String]("name")
          unbuilder.endObject()
          instance match {
            case OtherModuleInst   => Cabal.OtherModule(name)
            case ExposedModuleInst => Cabal.ExposedModule(name)
            case other =>
              deserializationError(s"Invalid `instance` field value ('$other').")
          }
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }

    override def write[J](obj: Cabal.Module, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("instance", obj match {
        case _: Cabal.OtherModule   => OtherModuleInst
        case _: Cabal.ExposedModule => ExposedModuleInst
      })
      builder.addField("name", obj.name)
      builder.endObject()
    }
  }

  implicit val ArtifactJsonFormat: JsonFormat[Cabal.Artifact] = new JsonFormat[Cabal.Artifact] {
    val LibraryInst = "Library"
    val ExecutableInst = "Executable"
    val TestSuiteInst = "TestSuite"
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Cabal.Artifact = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val name = unbuilder.readField[String]("name")
          val sourceDirectories = unbuilder.readField[Seq[String]]("sourceDirectories")
          val modules = unbuilder.readField[Seq[Cabal.Module]]("modules")
          val buildDependencies = unbuilder.readField[Seq[String]]("buildDependencies")
          val mavenDependencies = unbuilder.readField[Seq[String]]("mavenDependencies")
          val mavenRepositories = unbuilder.readField[Seq[String]]("mavenRepositories")
          val gitDependencies = unbuilder.readField[Seq[GitDependency]]("gitDependencies")
          val hsMain = unbuilder.readField[Option[String]]("hsMain")
          val cppOptions = unbuilder.readField[Seq[String]]("cppOptions")
          val ghcOptions = unbuilder.readField[Seq[String]]("ghcOptions")
          val includeDirs = unbuilder.readField[Seq[String]]("includeDirs")
          val installIncludes = unbuilder.readField[Seq[String]]("installIncludes")
          val extensions = unbuilder.readField[Seq[String]]("extensions")
          val language = unbuilder.readField[String]("language")
          unbuilder.readField[String]("instance") match {
            case LibraryInst =>
              unbuilder.endObject()
              Cabal.Library(
                name,
                sourceDirectories,
                modules,
                buildDependencies,
                mavenDependencies,
                mavenRepositories,
                gitDependencies,
                cppOptions,
                ghcOptions,
                includeDirs,
                installIncludes,
                extensions,
                language
              )
            case ExecutableInst =>
              unbuilder.endObject()
              Cabal.Executable(
                name,
                sourceDirectories,
                modules,
                buildDependencies,
                mavenDependencies,
                mavenRepositories,
                gitDependencies,
                hsMain,
                cppOptions,
                ghcOptions,
                includeDirs,
                installIncludes,
                extensions,
                language
              )
            case TestSuiteInst =>
              val testSuiteType = Cabal.TestSuiteTypes.withName(unbuilder.readField[String]("testSuiteType"))
              unbuilder.endObject()
              Cabal.TestSuite(
                name,
                sourceDirectories,
                modules,
                buildDependencies,
                mavenDependencies,
                mavenRepositories,
                gitDependencies,
                hsMain,
                cppOptions,
                ghcOptions,
                includeDirs,
                installIncludes,
                extensions,
                language,
                testSuiteType
              )
            case other =>
              deserializationError(s"Invalid `instance` field value ('$other').")
          }
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: Cabal.Artifact, builder: Builder[J]): Unit = {
      builder.beginObject()
      obj match {
        case _: Cabal.Library =>
          builder.addField("instance", LibraryInst)
        case _: Cabal.Executable =>
          builder.addField("instance", ExecutableInst)
        case t: Cabal.TestSuite =>
          builder.addField("instance", TestSuiteInst)
          builder.addField("testSuiteType", t.testSuiteType.toString)
      }
      builder.addField("name", obj.name)
      builder.addField("sourceDirectories", obj.sourceDirectories)
      builder.addField("modules", obj.modules)
      builder.addField("buildDependencies", obj.buildDependencies)
      builder.addField("mavenDependencies", obj.mavenDependencies)
      builder.addField("mavenRepositories", obj.mavenRepositories)
      builder.addField("gitDependencies", obj.gitDependencies)
      builder.addField("hsMain", obj.hsMain)
      builder.addField("cppOptions", obj.cppOptions)
      builder.addField("ghcOptions", obj.ghcOptions)
      builder.addField("includeDirs", obj.includeDirs)
      builder.addField("installIncludes", obj.installIncludes)
      builder.addField("extensions", obj.extensions)
      builder.addField("language", obj.language)
      builder.endObject()
    }
  }

  def AbsArtifactJsonFormat[A <: Cabal.Artifact](implicit ct: ClassTag[A]): JsonFormat[A] = projectFormat[A, Cabal.Artifact](identity, {
    artifact => if (ct.runtimeClass.isInstance(artifact)) {
      artifact.asInstanceOf[A]
    } else deserializationError(s"Expected ${ct.runtimeClass.getName} but found ${artifact.getClass.getName}.")
  })

  implicit val LibraryJsonFormat: JsonFormat[Cabal.Library] = AbsArtifactJsonFormat
  implicit val ExecutableJsonFormat: JsonFormat[Cabal.Executable] = AbsArtifactJsonFormat
  implicit val TestSuiteJsonFormat: JsonFormat[Cabal.TestSuite] = AbsArtifactJsonFormat

  implicit val ResolvedJsonFormat: JsonFormat[Cabal.Resolved] = new JsonFormat[Cabal.Resolved] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Cabal.Resolved = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val classpath = unbuilder.readField[Seq[File]]("classpath")
          val freezeFile = unbuilder.readField[Option[File]]("freezeFile")
          unbuilder.endObject()
          Cabal.Resolved(classpath, freezeFile)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: Cabal.Resolved, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("classpath", obj.classpath)
      builder.addField("freezeFile", obj.freezeFile)
      builder.endObject()
    }
  }

  implicit val CabalJsonFormat: JsonFormat[Cabal] = new JsonFormat[Cabal] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Cabal = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val projectName = unbuilder.readField[String]("projectName")
          val projectVersion = unbuilder.readField[String]("projectVersion")
          val projectLibrary = unbuilder.readField[Option[Cabal.Library]]("projectLibrary")
          val executables = unbuilder.readField[Seq[Cabal.Executable]]("executables")
          val testSuites = unbuilder.readField[Seq[Cabal.TestSuite]]("testSuites")
          unbuilder.endObject()
          Cabal(projectName, projectVersion, projectLibrary, executables, testSuites)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: Cabal, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("projectName", obj.projectName)
      builder.addField("projectVersion", obj.projectVersion)
      builder.addField("projectLibrary", obj.projectLibrary)
      builder.addField("executables", obj.executables)
      builder.addField("testSuites", obj.testSuites)
      builder.endObject()
    }
  }

}

object JsonFormats extends JsonFormats
