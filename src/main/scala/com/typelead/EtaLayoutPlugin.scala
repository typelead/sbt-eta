package com.typelead

import sbt.{Def, _}
import Keys._

object EtaLayoutPlugin extends AutoPlugin {

  override def requires: Plugins = SbtEta
  override def trigger  = allRequirements

  import SbtEta.autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    baseDirectory in Eta := (sourceDirectory in Compile).value / "eta",
    sourceDirectory in EtaLib := (sourceDirectory in Compile).value / "eta",
    sourceDirectory in EtaTest := (sourceDirectory in Compile).value / "eta",
    target in Eta := target.value / "eta" / "dist"
  )

}
