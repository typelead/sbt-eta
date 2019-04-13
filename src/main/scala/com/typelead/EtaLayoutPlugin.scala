package com.typelead

import sbt.{Def, _}
import Keys._

object EtaLayoutPlugin extends AutoPlugin {

  override def requires: Plugins = SbtEta
  override def trigger  = allRequirements

  import SbtEta.autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    etaPackageDir := (sourceDirectory in Compile).value / "eta",
    etaSource in Compile := (sourceDirectory in Compile).value / "eta",
    etaTarget := target.value / "eta" / "dist"
  )

}
