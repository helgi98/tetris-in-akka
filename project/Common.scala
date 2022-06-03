import sbt.Keys._
import sbt._

object Common {
  // Dependency versions
  private val akkaVersion = "2.6.19"
  private val akkaHttpVersion = "10.2.9"
  private val akkaHttpCirceVersion = "1.39.2"
  private val doobieVersion = "1.0.0-RC1"
  private val flywayVersion = "8.5.11"
  private val http4sVersion = "0.23.12"
  private val pureConfigVersion = "0.17.1"

  // Transient dependency versions
  // ~ doobie
  private val h2Version = "1.4.200"
  private val postgresVersion = "42.3.6"
  // ~ http4s
  private val circeVersion = "0.14.2"
  private val logbackVersion = "1.2.11"

  private[this] def projectSettings = Seq(
    name := "tetris-in-akka"
  )

  final val settings: Seq[Setting[_]] =
    projectSettings ++ dependencySettings ++ compilerPlugins

  private[this] def dependencySettings = Seq(
    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    ) ++ scala3CrossDependencies
  )

  private[this] def scala3CrossDependencies = Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceVersion,
  ).map(_.cross(CrossVersion.for3Use2_13))

  private[this] def compilerPlugins = Seq(
  )
}
