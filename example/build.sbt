lazy val root = (project in file(".")).
  settings(
    inThisBuild(Seq(
      etaVersion    := "0.8.6b4",
      etlasUseLocal := false,
      etlasVersion  := "1.5.0.0",
      scalaVersion  := "2.12.8",
      version       := "0.1.0-SNAPSHOT"
    )),
    name := "example",
    exposedModules in EtaLib += "Example.Transform",
    libraryDependencies in EtaLib ++= Seq(
      eta("aeson"),
      eta("lens-aeson"),
      eta("lens"),
      eta("text"),
      "com.google.guava" % "guava" % "25.0-jre"
    ),
    resolvers in EtaLib ++= Seq(Resolver.jcenterRepo, Resolver.sonatypeRepo("public")),
    gitDependencies in EtaLib ++= Seq(
      git("eta-spark-core", "https://github.com/Jyothsnasrinivas/eta-spark-core", branch("master"))
    ),
    hsMain in EtaTest := Some("Example/TransformSpec.hs"),
    libraryDependencies in EtaTest ++= Seq(
      eta("hspec")
    ),
    extensions in EtaLib += "OverloadedStrings"
  )
  .enablePlugins(SbtEta)
