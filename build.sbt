name := "d-soc"
version := "0.1.0"
scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % "7.15.0",
  "org.scalatest" %% "scalatest" % "3.2.20" % "test"
)

addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "7.15.0" cross CrossVersion.full)
