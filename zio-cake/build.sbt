resolvers += Resolver.sonatypeRepo("releases")

organization := "io.univalence"
name := "zio-cake"
version := "0.0.1"
scalaVersion := "2.12.11"
maxErrors := 3

libraryDependencies += "dev.zio" %% "zio" % "1.0.0-RC20"

// Refine scalac params from tpolecat
scalacOptions --= Seq(
  "-Xfatal-warnings"
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("chk", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
