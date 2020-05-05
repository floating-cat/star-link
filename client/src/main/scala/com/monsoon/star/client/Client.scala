package com.monsoon.star.client

import com.monsoon.star.BootstrapUtil
import com.monsoon.star.client.config.ClientConfig
import pureconfig.ConfigSource

object Client {

  def main(args: Array[String]): Unit = {
    import pureconfig.generic.auto._
    import com.monsoon.star.config.CommonConfigReader._
    import com.monsoon.star.client.config.ClientConfigReader._
    val configEither = ConfigSource.default.load[ClientConfig]

    configEither match {
      case Right(config) =>
        val socketAddress = config.toSocks5InetSocketAddress
        val clientInitializer = new ClientInitializer(new ClientHandler(config.proxy))
        BootstrapUtil.server(socketAddress, clientInitializer)

      case Left(configReaderFailures) =>
        print(configReaderFailures.prettyPrint())
    }
  }
}
