package cl.monsoon.star.client

import java.nio.file.Path

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.client.config.ClientConfigParserUtil
import cl.monsoon.star.client.rule.Router

object Client {

  def run(configPath: Path): Unit = {
    // We need to use toAbsolutePath here in order to let the HOCON file
    // resolve the relative include files correctly
    // TODO check whether this is a bug in the HOCON project
    val configAbsolutePath = configPath.toAbsolutePath
    val configEither = ClientConfigParserUtil.parse(configAbsolutePath)

    configEither match {
      case Right(config) =>
        val socketAddress = config.toSocks5InetSocketAddress
        val router = new Router(config.rule)
        val clientInitializer = new ClientInitializer(new ClientHandler(config.proxy, router, config.testMode))
        BootstrapUtil.server(socketAddress, clientInitializer)

      case Left(configReaderFailures) =>
        Console.err.println(s"Failed to parse the config file: $configAbsolutePath\n")
        Console.err.println(configReaderFailures.prettyPrint())
        System.exit(1)
    }
  }
}
