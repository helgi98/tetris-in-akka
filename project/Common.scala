import sbt.Keys._
import sbt._

object Common {
  // Dependency versions
  private val akkaVersion = "2.6.19"
  private val akkaHttpVersion = "10.2.9"
  private val doobieVersion = "1.0.0-RC1"
  private val jwtVersion = "0.9.1"
  private val flywayVersion = "8.5.11"
  private val pureConfigVersion = "0.17.1"

  private val postgresVersion = "42.3.6"
  private val logbackVersion = "1.2.11"
  private val commonsCodecVersion = "1.15"

  private[this] def projectSettings = Seq(
    name := "tetris-in-akka"
  )

  final val settings: Seq[Setting[_]] =
    projectSettings ++ dependencySettings ++ compilerPlugins

  private[this] def dependencySettings = Seq(
    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.flywaydb" % "flyway-core" % flywayVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.postgresql" % "postgresql" % postgresVersion,
      "commons-codec" % "commons-codec" % commonsCodecVersion,
      "io.jsonwebtoken" % "jjwt" % jwtVersion,
    ) ++ scala3CrossDependencies
  )

  private[this] def scala3CrossDependencies = Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  ).map(_.cross(CrossVersion.for3Use2_13))

  private[this] def compilerPlugins = Seq(
  )
}
