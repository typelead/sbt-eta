lazy val root = (project in file(".")).
  settings(
    inThisBuild(Seq(
      version      := "0.3.0",
      organization := "com.typelead"
    )),
    name              := "sbt-eta",
    sbtPlugin         := true,
    crossSbtVersions  := Seq("0.13.18", "1.2.8"),
    description       := "sbt plugin to work with Eta projects",
    licenses          := Seq(("BSD 3-Clause", url("https://github.com/typelead/sbt-eta/blob/master/LICENSE"))),
    scalacOptions     := Seq("-feature", "-deprecation"),
    publishMavenStyle := false,
    bintrayRepository := "sbt-plugins",
    bintrayOrganization in bintray := None,
    resolvers += Resolver.jcenterRepo
  )
