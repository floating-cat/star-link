package cl.monsoon.star.client

import java.nio.file.Path

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.client.config.ClientConfigParserUtil
import cl.monsoon.star.client.rule.Router
import grizzled.slf4j.Logger
import org.apache.logging.log4j.core.config.Configurator

object Client {

  private val logger = Logger[this.type]()

  def run(configPath: Path): Unit = {
    runSuspend(configPath)()
  }

  // JVM doesn't GC local variables
  // so we return a function here to
  // exit the scope for these variables
  def runSuspend(configPath: Path): () => Unit = {
    // we need to use toAbsolutePath here in order to let the HOCON file
    // resolve the relative include files correctly
    // TODO check whether this is a bug in the HOCON project
    val configAbsolutePath = configPath.toAbsolutePath
    val configEither = ClientConfigParserUtil.parse(configAbsolutePath)

    configEither match {
      case Right(config) =>
        val socketAddress = config.toSocks5InetSocketAddress
        val router = new Router(config.ruleOrDefault)
        val clientInitializer = new ClientInitializer(new ClientHandler(config.proxy, router, config.testMode))
        Configurator.setRootLevel(config.logLevel)
        logger.info("star-link client start")

        // don't let the closure capture the config
        val systemProxy = config.systemProxy
        () =>
          BootstrapUtil.server(socketAddress, clientInitializer,
            if (systemProxy) {
              SystemProxy.enable(socketAddress)
              SystemProxy.addDisablingHook()
            })

      case Left(configReaderFailures) =>
        Console.err.println(s"Failed to parse the config file: $configAbsolutePath\n")
        Console.err.println(configReaderFailures.prettyPrint())
        System.exit(1)
        throw new IllegalStateException()
    }
  }
}
