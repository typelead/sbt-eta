lazy val root = (project in file(".")).
  settings(
    inThisBuild(Seq(
      scalaVersion := "2.12.8",
      version      := "0.1.0-SNAPSHOT"
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
    hsMain in EtaTest := Some("TransformSpec.hs"),
    libraryDependencies in EtaTest ++= Seq(
      eta("hspec")
    ),
    extensions in EtaLib += "OverloadedStrings"
  )
