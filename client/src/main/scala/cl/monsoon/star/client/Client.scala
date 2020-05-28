package cl.monsoon.star.client

import java.nio.file.Path

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.client.config.ClientConfigParserUtil
import cl.monsoon.star.client.rule.Router

object Client {

  def run(config: Path): Unit = {
    val configEither = ClientConfigParserUtil.parse(config)

    configEither match {
      case Right(config) =>
        val socketAddress = config.toSocks5InetSocketAddress
        val router = new Router(config.rule)
        val clientInitializer = new ClientInitializer(new ClientHandler(config.proxy, router, config.testMode))
        BootstrapUtil.server(socketAddress, clientInitializer)

      case Left(configReaderFailures) =>
        Console.err.println(s"Failed to parse the config file: ${config.toRealPath()}\n")
        Console.err.println(configReaderFailures.prettyPrint())
        System.exit(1)
    }
  }
}
