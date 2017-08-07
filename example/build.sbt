lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
                  scalaVersion := "2.12.1",
                  version      := "0.1.0-SNAPSHOT"
                )),
    name := "example"
  )
