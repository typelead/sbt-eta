lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
                  version      := "0.1.0",
                  organization := "com.typelead",
                  scalaVersion := "2.10.6"
                )),
    name              := "sbt-eta",
    sbtPlugin         := true,
    description       := "sbt plugin to work with Eta projects",
    licenses          := Seq(("BSD 3-Clause", url("https://github.com/typelead/sbt-eta/blob/master/LICENSE"))),
    scalacOptions     := Seq("-feature", "-deprecation"),
    publishMavenStyle := false,
    bintrayRepository := "sbt-plugins",
    bintrayOrganization in bintray := None
  )
