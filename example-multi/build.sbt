lazy val hio = (project in file("hio"))
  .settings(
    name := "hio",
    version := "0.1.0-SNAPSHOT",
    modules in EtaLib += exposed("Hello.Mod"),
    libraryDependencies in EtaLib ++= Seq(
      eta("text", "1.2.3.0"),
      eta("bytestring", "0.10.8.2")
    )
  )
  .enablePlugins(SbtEta)
  .dependsOn(`java-deep`)

lazy val `java-deep` = (project in file("java-deep"))
  .settings(
    name := "java-deep",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.7"
  )

lazy val `java-shallow` = (project in file("java-shallow"))
  .settings(
    name := "java-shallow",
    libraryDependencies += "com.google.guava" % "guava" % "19.0"
  )
  .dependsOn(hio)

lazy val multi = (project in file("multi"))
  .settings(
    name := "multi",
    hsMain in EtaExe := Some("Main.hs"),
    libraryDependencies in EtaExe ++= Seq(
      eta("text", "1.2.3.0"),
      eta("memory", "0.14.14")
    )
  )
  .enablePlugins(SbtEta)
  .dependsOn(hio, `java-shallow`)

lazy val root = (project in file("."))
  .aggregate(`multi`, `java-deep`, `java-shallow`, `hio`)
