name := "star-link"
ThisBuild / organization := "cl.monsoon"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.13.2"
val projectPackageName = "cl.monsoon.star"

lazy val root = project in file(".")

lazy val link = project
  .dependsOn(client, server)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](root / name, version),
    buildInfoPackage := projectPackageName,
    libraryDependencies += "com.monovore" %% "decline" % "1.2.0",

    mainClass in Compile := Some(s"$projectPackageName.link.Star"),
    discoveredMainClasses in Compile := Seq(),
    jlinkIgnoreMissingDependency := JlinkIgnore.everything,
    // We need access to sun.misc to see if direct buffers are available for Netty
    jlinkModules ++= List("jdk.unsupported"),
    bashScriptConfigLocation := Some("${app_home}/../conf/application.ini"),

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
    },
    packageName in Docker := "star-link",
    dockerUsername := Some("aasterism"),
    dockerUpdateLatest := true,
    dockerEntrypoint := Seq("/opt/docker/bin/link", "-s", "/etc/star-link/server.conf",
      "-J-Xmx20m", "-J-XX:MaxDirectMemorySize=20m"),
    dockerExposedVolumes := Seq("/opt/docker/logs")
  )
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(JlinkPlugin)
  .enablePlugins(GraalVMNativeImagePlugin)
  .enablePlugins(DockerPlugin)

lazy val common = project
  .settings(
    libraryDependencies ++= commonDependencies,
  )
  .settings(valueDiscardSetting)

lazy val client = project
  .dependsOn(common)
  .settings(valueDiscardSetting)

lazy val server = project
  .dependsOn(common)
  .settings(valueDiscardSetting)

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

  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.13.3",
  "org.clapper" %% "grizzled-slf4j" % "1.3.4",
)

lazy val valueDiscardSetting =
  Seq(scalacOptions ~= (_.filterNot(Set("-Wvalue-discard"))))
