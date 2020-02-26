val ZioVersion        = "1.0.0-RC17"
val Specs2Version     = "4.7.0"
val Http4sVersion     = "0.21.1"
val CirceVersion      = "0.13.0"
val PureConfigVersion = "0.12.2"

resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.sonatypeRepo("snapshots")

cancelable in Global := true

lazy val root = (project in file("."))
  .settings(
    organization := "szczyp",
    name := "orco",
    version := "0.0.1",
    scalaVersion := "2.13.1",
    maxErrors := 3,
    libraryDependencies ++= Seq(
      "dev.zio"               %% "zio"                  % ZioVersion,
      "dev.zio"               %% "zio-interop-cats"     % "2.0.0.0-RC10",
      "org.http4s"            %% "http4s-blaze-server"  % Http4sVersion,
      "org.http4s"            %% "http4s-blaze-client"  % Http4sVersion,
      "org.http4s"            %% "http4s-circe"         % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"           % Http4sVersion,
      "io.circe"              %% "circe-generic"        % CirceVersion,
      "io.circe"              %% "circe-generic-extras" % CirceVersion,
      "com.github.pureconfig" %% "pureconfig"           % PureConfigVersion,
      "org.specs2"            %% "specs2-core"          % Specs2Version % "test"
    )
  )

// Refine scalac params from tpolecat
scalacOptions --= Seq(
  "-Xfatal-warnings",
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("chk", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
