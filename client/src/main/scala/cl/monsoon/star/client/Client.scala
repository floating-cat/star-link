package cl.monsoon.star.client

import java.nio.file.Path

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.client.config.ClientConfigParserUtil

object Client {

  def run(config: Path): Unit = {
    val configEither = ClientConfigParserUtil.parse(config)

    configEither match {
      case Right(config) =>
        val socketAddress = config.toSocks5InetSocketAddress
        val clientInitializer = new ClientInitializer(new ClientHandler(config.proxy, config.testMode))
        BootstrapUtil.server(socketAddress, clientInitializer)

      case Left(configReaderFailures) =>
        print(configReaderFailures.prettyPrint())
    }
  }
}
