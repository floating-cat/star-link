package cl.monsoon.star.server

import java.net.InetSocketAddress
import java.nio.file.Path

import cl.monsoon.star.{BootstrapUtil, BuildInfo}
import cl.monsoon.star.server.config.ServerConfig
import grizzled.slf4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import pureconfig.ConfigSource

object Server {

  private val logger = Logger[this.type]()

  def run(configPath: Path): Unit = {
    runSuspend(configPath)()
  }

  // JVM doesn't GC local variables
  // so we return a function here to
  // exit the scope for these variables
  def runSuspend(configPath: Path): () => Unit = {
    import pureconfig.generic.auto._
    import cl.monsoon.star.config.CommonConfigReader._
    val configAbsolutePath = configPath.toAbsolutePath
    val configEither = ConfigSource.file(configAbsolutePath).load[ServerConfig]

    configEither match {
      case Right(config) =>
        val socketAddress = new InetSocketAddress(config.listenIp.toInetAddress, config.listenPort.value)
        Configurator.setRootLevel(config.logLevel)
        logger.info(s"star-link server v${BuildInfo.version} start")
        () => BootstrapUtil.server(socketAddress, new ServerInitializer(config))

      case Left(configReaderFailures) =>
        Console.err.println(s"Failed to parse the config file: $configAbsolutePath\n")
        Console.err.println(configReaderFailures.prettyPrint())
        System.exit(1)
        throw new IllegalStateException()
    }
  }
}
