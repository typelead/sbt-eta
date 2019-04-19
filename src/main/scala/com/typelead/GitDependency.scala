package com.typelead

final case class GitDependency(packageName: String, location: String, resolver: GitDependency.Resolver, subDir: Option[String])

object GitDependency {

  sealed trait Resolver
  final case class Branch(branch: String) extends Resolver
  final case class Commit(commit: String) extends Resolver
  final case class Tag(tag: String)       extends Resolver

}