name := """naumen-test"""
organization := "Seva"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.2"

lazy val doobie = "0.8.8"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "org.tpolecat" %% "doobie-core" % doobie
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % doobie
libraryDependencies += "org.tpolecat" %% "doobie-hikari" % doobie
libraryDependencies += "org.tpolecat" %% "doobie-refined" % doobie
libraryDependencies += jdbc
libraryDependencies += filters

PlayKeys.devSettings := Seq("play.server.http.port" -> "9090")

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "Seva.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "Seva.binders._"
