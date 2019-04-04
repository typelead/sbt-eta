lazy val root = (project in file(".")).
  settings(
    inThisBuild(Seq(
      scalaVersion := "2.12.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "example"
  )
