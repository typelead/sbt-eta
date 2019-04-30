package com.typelead

import sbt._
import sbt.serialization._

import scala.pickling.FastTypeTag
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

trait JsonFormats {

  private def write[A](builder: PBuilder, field: String, value: A)(implicit tag: FastTypeTag[A], pickler: Pickler[A]): PBuilder = {
    builder.putField(field, { b =>
      b.hintTag(tag)
      pickler.pickle(value, b)
    })
  }
  private def read[A](reader: PReader, field: String)(implicit unpickler: Unpickler[A]): A = {
    unpickler.unpickleEntry(reader.readField(field)).asInstanceOf[A]
  }

  private implicit val optOfStringTag: FastTypeTag[Option[String]] = FastTypeTag.apply
  private implicit val moduleIdTag: FastTypeTag[ModuleID] = FastTypeTag.apply
  private implicit val seqOfModuleIdTag: FastTypeTag[Seq[ModuleID]] = FastTypeTag.apply
  private implicit val seqOfFileTag: FastTypeTag[Seq[File]] = FastTypeTag.apply
  private implicit val seqOfStringTag: FastTypeTag[Seq[String]] = FastTypeTag.apply

  implicit val ResolverJsonFormat: Pickler[GitDependency.Resolver] with Unpickler[GitDependency.Resolver] = {
    new Pickler[GitDependency.Resolver] with Unpickler[GitDependency.Resolver] {
      val BranchInst = "Branch"
      val CommitInst = "Commit"
      val TagInst = "Tag"
      override val tag: FastTypeTag[GitDependency.Resolver] = FastTypeTag.apply
      override def pickle(r: GitDependency.Resolver, builder: PBuilder): Unit = {
        builder.pushHints()
        builder.hintTag(tag)
        builder.beginEntry(r)
        r match {
          case GitDependency.Branch(branch) =>
            write(builder, "instance", BranchInst)
            write(builder, "branch", branch)
          case GitDependency.Commit(commit) =>
            write(builder, "instance", CommitInst)
            write(builder, "commit", commit)
          case GitDependency.Tag(tag) =>
            write(builder, "instance", TagInst)
            write(builder, "tag", tag)
        }
        builder.endEntry()
        builder.popHints()
      }
      override def unpickle(tag: String, reader: PReader): Any = {
        reader.pushHints()
        // reader.hintTag(tag)
        reader.beginEntry()
        val result = read[String](reader, "instance") match {
          case BranchInst =>
            val branch = read[String](reader, "branch")
            GitDependency.Branch(branch)
          case CommitInst =>
            val commit = read[String](reader, "commit")
            GitDependency.Commit(commit)
          case TagInst =>
            val tag = read[String](reader, "tag")
            GitDependency.Tag(tag)
          case other =>
            throw new IllegalArgumentException(s"Invalid `instance` field value ('$other').")
        }
        reader.endEntry()
        reader.popHints()
        result
      }
    }
  }

  implicit val GitDependencyJsonFormat: Pickler[GitDependency] with Unpickler[GitDependency] = PicklerUnpickler.generate[GitDependency]

  implicit val DependencyJsonFormat: Pickler[EtaPackage.Dependency] with Unpickler[EtaPackage.Dependency] = {
    new Pickler[EtaPackage.Dependency] with Unpickler[EtaPackage.Dependency] {
      val MavenDependencyInst = "MavenDependency"
      val LibraryDependencyInst = "LibraryDependency"
      override val tag: FastTypeTag[EtaPackage.Dependency] = FastTypeTag.apply
      override def pickle(d: EtaPackage.Dependency, builder: PBuilder): Unit = {
        builder.pushHints()
        builder.hintTag(tag)
        builder.beginEntry(d)
        d match {
          case EtaPackage.MavenDependency(moduleId) =>
            write(builder, "instance", MavenDependencyInst)
            write(builder, "moduleId", moduleId)
          case EtaPackage.LibraryDependency(name, mavenDeps, libraryJars, dependencies) =>
            write(builder, "instance", LibraryDependencyInst)
            write(builder, "name", name)
            write(builder, "mavenDeps", mavenDeps)
            write(builder, "libraryJars", libraryJars)
            write(builder, "dependencies", dependencies)
        }
        builder.endEntry()
        builder.popHints()
      }
      override def unpickle(tag: String, reader: PReader): Any = {
        reader.pushHints()
        // reader.hintTag(tag)
        reader.beginEntry()
        val result = read[String](reader, "instance") match {
          case MavenDependencyInst =>
            val moduleId = read[ModuleID](reader, "moduleId")
            EtaPackage.MavenDependency(moduleId)
          case LibraryDependencyInst =>
            val name = read[String](reader, "name")
            val mavenDeps = read[Seq[ModuleID]](reader, "mavenDeps")
            val libraryJars = read[Seq[File]](reader, "libraryJars")
            val dependencies = read[Seq[String]](reader, "dependencies")
            EtaPackage.LibraryDependency(name, mavenDeps, libraryJars, dependencies)
          case other =>
            throw new IllegalArgumentException(s"Invalid `instance` field value ('$other').")
        }
        reader.endEntry()
        reader.popHints()
        result
      }
    }
  }

  implicit val EtaPackageJsonFormat: Pickler[EtaPackage] with Unpickler[EtaPackage] = PicklerUnpickler.generate[EtaPackage]

  implicit val ModuleJsonFormat: Pickler[Cabal.Module] with Unpickler[Cabal.Module] = {
    new Pickler[Cabal.Module] with Unpickler[Cabal.Module] {
      val OtherModuleInst = "OtherModule"
      val ExposedModuleInst = "ExposedModule"
      override val tag: FastTypeTag[Cabal.Module] = FastTypeTag.apply
      override def pickle(m: Cabal.Module, builder: PBuilder): Unit = {
        builder.pushHints()
        builder.hintTag(tag)
        builder.beginEntry(m)
        write(builder, "instance", m match {
          case _: Cabal.OtherModule   => OtherModuleInst
          case _: Cabal.ExposedModule => ExposedModuleInst
        })
        write(builder, "name", m.name)
        builder.endEntry()
        builder.popHints()
      }
      override def unpickle(tag: String, reader: PReader): Any = {
        reader.pushHints()
        // reader.hintTag(tag)
        reader.beginEntry()
        val instance = read[String](reader, "instance")
        val name = read[String](reader, "name")
        val result = instance match {
          case OtherModuleInst   => Cabal.OtherModule(name)
          case ExposedModuleInst => Cabal.ExposedModule(name)
          case other =>
            throw new IllegalArgumentException(s"Invalid `instance` field value ('$other').")
        }
        reader.endEntry()
        reader.popHints()
        result
      }
    }
  }

  implicit val ArtifactJsonFormat: Pickler[Cabal.Artifact] with Unpickler[Cabal.Artifact] = {
    new Pickler[Cabal.Artifact] with Unpickler[Cabal.Artifact] {
      val LibraryInst = "Library"
      val ExecutableInst = "Executable"
      val TestSuiteInst = "TestSuite"
      implicit val seqOfModuleTag: FastTypeTag[Seq[Cabal.Module]] = FastTypeTag.apply
      implicit val seqOfGitDependencyTag: FastTypeTag[Seq[GitDependency]] = FastTypeTag.apply
      override val tag: FastTypeTag[Cabal.Artifact] = FastTypeTag.apply
      override def pickle(a: Cabal.Artifact, builder: PBuilder): Unit = {
        builder.pushHints()
        builder.hintTag(tag)
        builder.beginEntry(a)
        a match {
          case _: Cabal.Library =>
            write(builder, "instance", LibraryInst)
          case _: Cabal.Executable =>
            write(builder, "instance", ExecutableInst)
          case t: Cabal.TestSuite =>
            write(builder, "instance", TestSuiteInst)
            write(builder, "testSuiteType", t.testSuiteType.toString)
        }
        write(builder, "name", a.name)
        write(builder, "sourceDirectories", a.sourceDirectories)
        builder.putField("modules", { b =>
          b.hintTag(seqOfModuleTag)
          implicitly[Pickler[Seq[Cabal.Module]]].pickle(a.modules, b)
        })
        write(builder, "buildDependencies", a.buildDependencies)
        write(builder, "mavenDependencies", a.mavenDependencies)
        write(builder, "mavenRepositories", a.mavenRepositories)
        write(builder, "gitDependencies", a.gitDependencies)
        write(builder, "hsMain", a.hsMain)
        write(builder, "cppOptions", a.cppOptions)
        write(builder, "ghcOptions", a.ghcOptions)
        write(builder, "includeDirs", a.includeDirs)
        write(builder, "installIncludes", a.installIncludes)
        write(builder, "extensions", a.extensions)
        write(builder, "language", a.language)
        builder.endEntry()
        builder.popHints()
      }
      override def unpickle(tag: String, reader: PReader): Any = {
        reader.pushHints()
        // reader.hintTag(tag)
        reader.beginEntry()
        val name = read[String](reader, "name")
        val sourceDirectories = read[Seq[String]](reader, "sourceDirectories")
        val modules = read[Seq[Cabal.Module]](reader, "modules")
        val buildDependencies = read[Seq[String]](reader, "buildDependencies")
        val mavenDependencies = read[Seq[String]](reader, "mavenDependencies")
        val mavenRepositories = read[Seq[String]](reader, "mavenRepositories")
        val gitDependencies = read[Seq[GitDependency]](reader, "gitDependencies")
        val hsMain = read[Option[String]](reader, "hsMain")
        val cppOptions = read[Seq[String]](reader, "cppOptions")
        val ghcOptions = read[Seq[String]](reader, "ghcOptions")
        val includeDirs = read[Seq[String]](reader, "includeDirs")
        val installIncludes = read[Seq[String]](reader, "installIncludes")
        val extensions = read[Seq[String]](reader, "extensions")
        val language = read[String](reader, "language")
        val result = read[String](reader, "instance") match {
          case LibraryInst =>
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
            val testSuiteType = Cabal.TestSuiteTypes.withName(read[String](reader, "testSuiteType"))
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
            throw new IllegalArgumentException(s"Invalid `instance` field value ('$other').")
        }
        reader.endEntry()
        reader.popHints()
        result
      }
    }
  }

  def AbsArtifactJsonFormat[A <: Cabal.Artifact : TypeTag](implicit ct: ClassTag[A]): Pickler[A] with Unpickler[A] = {
    new Pickler[A] with Unpickler[A] {
      override def tag: FastTypeTag[A] = FastTypeTag[A]
      override def pickle(a: A, builder: PBuilder): Unit = {
        ArtifactJsonFormat.pickle(a, builder)
      }
      override def unpickle(tag: String, reader: PReader): Any = {
        val artifact = ArtifactJsonFormat.unpickle(tag, reader)
        if (ct.runtimeClass.isInstance(artifact)) {
          artifact.asInstanceOf[A]
        } else throw new IllegalArgumentException(s"Expected ${ct.runtimeClass.getName} but found ${artifact.getClass.getName}.")
      }
    }
  }

  implicit val LibraryJsonFormat: Pickler[Cabal.Library] with Unpickler[Cabal.Library] = AbsArtifactJsonFormat
  implicit val ExecutableJsonFormat: Pickler[Cabal.Executable] with Unpickler[Cabal.Executable] = AbsArtifactJsonFormat
  implicit val TestSuiteJsonFormat: Pickler[Cabal.TestSuite] with Unpickler[Cabal.TestSuite] = AbsArtifactJsonFormat

  implicit val ResolvedJsonFormat: Pickler[Cabal.Resolved] with Unpickler[Cabal.Resolved] = PicklerUnpickler.generate[Cabal.Resolved]
  implicit val CabalJsonFormat: Pickler[Cabal] with Unpickler[Cabal] = PicklerUnpickler.generate[Cabal]

}

object JsonFormats extends JsonFormats
