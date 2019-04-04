lazy val root = project.in(file(".")).dependsOn(etaPlugin)

lazy val etaPlugin = RootProject(file("../.."))

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")
