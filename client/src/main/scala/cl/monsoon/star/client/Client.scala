package cl.monsoon.star.client

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.client.config.ClientConfig
import pureconfig.ConfigSource

object Client {

  def main(args: Array[String]): Unit = {
    import pureconfig.generic.auto._
    import cl.monsoon.star.config.CommonConfigReader._
    import cl.monsoon.star.client.config.ClientConfigReader._
    val configEither = ConfigSource.default.load[ClientConfig]

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
