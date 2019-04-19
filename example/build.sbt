lazy val root = (project in file(".")).
  settings(
    inThisBuild(Seq(
      scalaVersion := "2.12.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "example",
    etaVersion := "0.8.6b5",
    exposedModules in EtaLib += "Example.Transform",
    libraryDependencies in EtaLib ++= Seq(
      eta("aeson"),
      eta("lens-aeson"),
      eta("lens"),
      eta("text"),
      "com.google.guava" % "guava" % "25.0-jre"
    ),
    gitDependencies in EtaLib ++= Seq(
      git("eta-spark-core", "https://github.com/Jyothsnasrinivas/eta-spark-core", branch("master"))
    ),
    hsMain in EtaTest := Some("Example/TransformSpec.hs"),
    libraryDependencies in EtaTest ++= Seq(
      eta("hspec")
    ),
    extensions in EtaLib += "OverloadedStrings"
  )
