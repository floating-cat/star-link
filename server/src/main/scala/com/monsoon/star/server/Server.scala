package com.monsoon.star.server

import java.net.InetSocketAddress

import com.monsoon.star.BootstrapUtil
import com.monsoon.star.server.config.ServerConfig
import pureconfig.ConfigSource

object Server {

  def main(args: Array[String]): Unit = {
    import pureconfig.generic.auto._
    import com.monsoon.star.config.CommonConfigReader._
    val configEither = ConfigSource.default.load[ServerConfig]

    configEither match {
      case Right(config) =>
        val general = config.general
        val socketAddress = new InetSocketAddress(general.listenIp.toInetAddress, general.port.value)
        BootstrapUtil.server(socketAddress, new ServerInitializer(general.password))

      case Left(configReaderFailures) =>
        print(configReaderFailures.prettyPrint())
    }
  }
}
