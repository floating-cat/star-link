name := "star-link"
ThisBuild / organization := "cl.monsoon"
ThisBuild / version := "0.1.2"
ThisBuild / scalaVersion := "2.13.3"
val projectPackageName = "cl.monsoon.star"

lazy val root: Project = (project in file("."))
  .aggregate(link, client, server, common)

lazy val link = project
  .dependsOn(client, server)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](root / name, version),
    buildInfoPackage := projectPackageName,
    libraryDependencies += "com.monovore" %% "decline" % "1.3.0",

    mainClass in Compile := Some(s"$projectPackageName.link.Star"),
    discoveredMainClasses in Compile := Seq(),
    bashScriptConfigLocation := Some("${app_home}/../conf/application_unix.ini"),
    batScriptConfigLocation := Some("%APP_HOME%\\conf\\application_win.ini"),

    graalVMNativeImageCommand := "/usr/lib/jvm/java-11-graalvm/bin/native-image",
    graalVMNativeImageOptions ++= {
      val graalPath = sourceDirectory.value / "graal"
      Seq(
        "--no-server",
        "-H:+TraceClassInitialization",
        "-H:+ReportExceptionStackTraces",
        "-H:+RemoveSaturatedTypeFlows",
        "-H:JNIConfigurationFiles=" + graalPath / "jni-config.json",
        "-H:DynamicProxyConfigurationFiles=" + graalPath / "proxy-config.json",
        "-H:ReflectionConfigurationFiles=" + graalPath / "reflect-config.json",
        "-H:ResourceConfigurationFiles=" + graalPath / "resource-config.json",
      )
    },
    packageName in Docker := "star-link",
    dockerUsername := Some("aasterism"),
    dockerUpdateLatest := true,
    dockerBaseImage := "adoptopenjdk/openjdk11:debianslim-jre",
    dockerEntrypoint := Seq("/opt/docker/bin/link", "-s", "/etc/star-link/server.conf",
      "-J-Xmx30m", "-J-XX:MaxDirectMemorySize=30m"),
    dockerExposedVolumes := Seq("/opt/docker/logs")
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(GraalVMNativeImagePlugin)
  .enablePlugins(DockerPlugin)

lazy val common = project
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](root / name, version),
    buildInfoPackage := projectPackageName,
    valueDiscardSetting,
    libraryDependencies ++= Seq(
      "io.netty" % "netty-all" % nettyVersion,
      // TODO
      "io.netty" % "netty-transport-native-epoll" % nettyVersion classifier "linux-x86_64",
      "io.netty" % "netty-transport-native-kqueue" % nettyVersion classifier "osx-x86_64",
      "io.netty" % "netty-tcnative-boringssl-static" % "2.0.34.Final",

      "commons-codec" % "commons-codec" % "1.15",

      "com.github.pureconfig" %% "pureconfig" % "0.14.0",
      "com.github.seancfoley" % "ipaddress" % "5.3.3",

      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.13.3",
      "org.clapper" %% "grizzled-slf4j" % "1.3.4",
    )
  )
  .enablePlugins(BuildInfoPlugin)

lazy val client = project
  .dependsOn(common)
  .settings(
    valueDiscardSetting,
    libraryDependencies += "net.java.dev.jna" % "jna" % "5.6.0",
  )

lazy val server = project
  .dependsOn(common)
  .settings(valueDiscardSetting)

lazy val nettyVersion = "4.1.52.Final"

lazy val valueDiscardSetting =
  Seq(scalacOptions ~= (_.filterNot(Set("-Wvalue-discard"))))
