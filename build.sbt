name := "Datapiece"

version := "0.1"

scalaVersion := "2.11.4"

lazy val checksame = taskKey[Unit]("Execute the shell script")

checksame := {
  "./check_same.sh" !
}


libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.2.2"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.10"

libraryDependencies += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.6.0"

libraryDependencies += "com.github.scopt" % "scopt_2.11" % "3.3.0"

libraryDependencies += "com.github.pathikrit" %% "better-files" % "2.13.0"

scalariformSettings
