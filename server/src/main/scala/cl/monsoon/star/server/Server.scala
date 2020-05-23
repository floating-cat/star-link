package cl.monsoon.star.server

import java.net.InetSocketAddress
import java.nio.file.Path

import cl.monsoon.star.BootstrapUtil
import cl.monsoon.star.server.config.ServerConfig
import pureconfig.ConfigSource

object Server {

  def run(config: Path): Unit = {
    import pureconfig.generic.auto._
    import cl.monsoon.star.config.CommonConfigReader._
    val configEither = ConfigSource.file(config).load[ServerConfig]

    configEither match {
      case Right(config) =>
        val socketAddress = new InetSocketAddress(config.listenIp.toInetAddress,config.listenPort.value)
        BootstrapUtil.server(socketAddress, new ServerInitializer(config))

      case Left(configReaderFailures) =>
        Console.err.println(s"Failed to parse the config file: ${config.toRealPath()}\n")
        Console.err.println(configReaderFailures.prettyPrint())
    }
  }
}
