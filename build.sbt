name := "star-link"
ThisBuild / organization := "cl.monsoon"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.13.2"
val packageName = "cl.monsoon.star"

lazy val root = project in file(".")

lazy val link = project
  .dependsOn(client, server)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](root / name, version),
    buildInfoPackage := packageName,
    libraryDependencies += "com.monovore" %% "decline" % "1.2.0",

    mainClass in Compile := Some(s"$packageName.link.Star"),
    graalVMNativeImageCommand := "/usr/lib/jvm/java-11-graalvm/bin/native-image",
    graalVMNativeImageOptions ++= {
      val graalPath = sourceDirectory.value / "graal"
      List(
        "--no-server",
        "-H:+TraceClassInitialization",
        "-H:+ReportExceptionStackTraces",
        "-H:+RemoveSaturatedTypeFlows",
        s"-H:JNIConfigurationFiles=" + graalPath / "jni-config.json",
        s"-H:DynamicProxyConfigurationFiles=" + graalPath / "proxy-config.json",
        s"-H:ReflectionConfigurationFiles=" + graalPath / "reflect-config.json",
        s"-H:ResourceConfigurationFiles=" + graalPath / "resource-config.json",
      )
    }
  )
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GraalVMNativeImagePlugin)

lazy val common = project
  .settings(
    libraryDependencies ++= commonDependencies
  )

lazy val client = project
  .dependsOn(common)

lazy val server = project
  .dependsOn(common)

val nettyVersion = "4.1.50.Final"
lazy val commonDependencies = Seq(
  "io.netty" % "netty-all" % nettyVersion,
  // TODO
  "io.netty" % "netty-transport-native-epoll" % nettyVersion classifier "linux-x86_64",
  "io.netty" % "netty-transport-native-kqueue" % nettyVersion classifier "osx-x86_64",
  "io.netty" % "netty-tcnative-boringssl-static" % "2.0.30.Final",

  "commons-codec" % "commons-codec" % "1.14",

  "com.github.pureconfig" %% "pureconfig" % "0.12.3",
  "com.github.seancfoley" % "ipaddress" % "5.3.1",

  "org.apache.logging.log4j" % "log4j-api" % "2.13.3",
  "org.apache.logging.log4j" % "log4j-core" % "2.13.3",
)
