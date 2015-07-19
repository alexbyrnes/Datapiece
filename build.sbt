name := "Piece"

version := "0.1"

scalaVersion := "2.11.4"

//libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0"

//resolvers += Resolver.sonatypeRepo("public")

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.10"


TaskKey[File]("mkrun") <<= (baseDirectory, fullClasspath in Runtime, mainClass in Runtime) map { (base, cp, main) =>
  val template = """#!/bin/sh
java -classpath "%s" %s "$@"
"""
  val mainStr = main getOrElse error("No main class specified")
  val contents = template.format(cp.files.absString, mainStr)
  val out = base / "run-server.sh"
  IO.write(out, contents)
  out.setExecutable(true)
  out
}


