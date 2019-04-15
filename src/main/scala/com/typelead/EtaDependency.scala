package com.typelead

import sbt._

object EtaDependency {

  private [typelead] val ETA_ORG_INTERNAL = "com.typelead.eta##interanal"
  private [typelead] val LATEST_VERSION = "latest"

  private val isEtaDependency: ModuleID => Boolean = _.organization == ETA_ORG_INTERNAL

  def apply(packageName: String): ModuleID = apply(packageName, EtaDependency.LATEST_VERSION)
  def apply(packageName: String, version: String): ModuleID = EtaDependency.ETA_ORG_INTERNAL % packageName % version

  def getAllEtaDependencies(dependencies: Seq[ModuleID]): Seq[ModuleID] =
    dependencies.filter(isEtaDependency).distinct

  def getAllMavenDependencies(dependencies: Seq[ModuleID]): Seq[ModuleID] =
    dependencies.filterNot(isEtaDependency).distinct

  // Version ranges

  case class Version(parts: Seq[Int]) {
    def increment: Version = {
      if (parts.isEmpty) this
      else Version(parts.init :+ (parts.last + 1))
    }
    override def toString: String = parts.mkString(".")
  }
  case class VersionBound(version: Version, inclusive: Boolean) {
    def getBound(opIncl: String, opExcl: String): String = {
      (if (inclusive) opIncl else opExcl) + " " + version.toString
    }
  }
  case class VersionRange(lowerBound: Option[VersionBound], upperBound: Option[VersionBound]) {
    override def toString: String = {
      if (lowerBound.map(_.version) == upperBound.map(_.version)) {
        lowerBound.map(bound => "== " + bound.version).getOrElse("")
      } else {
        (lowerBound.map(_.getBound(">=", ">")).toList ++ upperBound.map(_.getBound("<=", "<")).toList).mkString(" && ")
      }
    }
  }

  def parseVersion(version: String): Version = {
    val NumericPattern = """([0-9]+)""".r
    version.split("\\.").foldLeft(Version(Nil)) {
      case (Version(parts), NumericPattern(numeric)) => Version(parts :+ numeric.toInt)
      case _ => throw new IllegalArgumentException("Invalid Version String: " + version)
    }
  }

  val BOUND_CHARS: Set[Char] = Set('[', ']', '(', ')')

  /**
    * "[a,b]" Matches all versions greater than or equal to a and less than or equal to b
    * "[a,b[" Matches all versions greater than or equal to a and less than than b
    * "]a,b]" Matches all versions greater than a and less than or equal to b
    * "]a,b[" Matches all versions greater than a and less than than b
    * "[a,)"  Matches all versions greater than or equal to a
    * "]a,)"  Matches all versions greater than a
    * "(,b]"  Matches all versions less than or equal to b
    * "(,b["  Matches all versions less than than b
    */
  def parseVersionRange(ivyVersion: String): VersionRange = {
    if (ivyVersion.isEmpty || ivyVersion == LATEST_VERSION) {
      VersionRange(None, None)
    } else {
      val first = ivyVersion.head
      if (BOUND_CHARS contains first) {
        val last = ivyVersion.last
        if (BOUND_CHARS contains last) {
          val inner = ivyVersion.drop(1).dropRight(1)
          val bounds = inner.split(',')
          val lastComma = inner.last == ','
          if (bounds.length == 2 || lastComma) {
            val lower = bounds.head
            val upper = if (lastComma) "" else bounds.last
            val lowerBound = first match {
              case ']' =>
                Some(VersionBound(parseVersion(lower), inclusive = false))
              case '[' =>
                Some(VersionBound(parseVersion(lower), inclusive = true))
              case ')' =>
                versionRangeError(ivyVersion, "')' cannot start a version range.")
              case '(' if lower.nonEmpty =>
                versionRangeError(ivyVersion, "Expected a ',' to come after a '('.")
              case '(' =>
                None
            }
            val upperBound = last match {
              case '[' =>
                Some(VersionBound(parseVersion(upper), inclusive = false))
              case ']' =>
                Some(VersionBound(parseVersion(upper), inclusive = true))
              case '(' =>
                versionRangeError(ivyVersion, "'(' cannot end a version range.")
              case ')' if upper.nonEmpty =>
                versionRangeError(ivyVersion, "Expected a ',' to precede a ')'.")
              case ')' =>
                None
            }
            VersionRange(lowerBound, upperBound)
          } else {
            versionRangeError(ivyVersion, "Expected a single occurrence of ','.")
          }
        } else {
          versionRangeError(ivyVersion, "Expected last character to be ']', '[', or ')'.")
        }
      } else if (ivyVersion.endsWith(".+")) {
        val lower = parseVersion(ivyVersion)
        VersionRange(Some(VersionBound(parseVersion(ivyVersion), inclusive = true)), Some(VersionBound(lower.increment, inclusive = false)))
      } else {
        val version = parseVersion(ivyVersion)
        VersionRange(Some(VersionBound(version, inclusive = true)), Some(VersionBound(version, inclusive = true)))
      }
    }
  }

  private def versionRangeError(versionRange: String, message: String): Nothing =
    throw new IllegalArgumentException("Invalid Version Range: '" + versionRange + "' - " + message)

  // Eta dependencies

  def toCabalDependency(dependency: ModuleID): String =
    dependency.name + " " + parseVersionRange(dependency.revision).toString

  val base = apply("base", "[4.7,5[")

}
