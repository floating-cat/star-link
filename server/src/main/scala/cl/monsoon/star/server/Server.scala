package cl.monsoon.star.server

import java.net.InetSocketAddress

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.server.config.ServerConfig
import pureconfig.ConfigSource

object Server {

  def main(args: Array[String]): Unit = {
    import pureconfig.generic.auto._
    import cl.monsoon.star.config.CommonConfigReader._
    val configEither = ConfigSource.default.load[ServerConfig]

    configEither match {
      case Right(config) =>
        val socketAddress = new InetSocketAddress(config.listenIp.toInetAddress,config.listenPort.value)
        BootstrapUtil.server(socketAddress, new ServerInitializer(config))

      case Left(configReaderFailures) =>
        print(configReaderFailures.prettyPrint())
    }
  }
}
