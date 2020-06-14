package cl.monsoon.star.client.config

import java.nio.file.Path

import cl.monsoon.star.client.config.ClientConfig.{DefaultAddress, DefaultSocks5Port}
import cl.monsoon.star.config.Port
import inet.ipaddr.IPAddress
import org.apache.logging.log4j.Level
import pureconfig.ConfigReader.Result
import pureconfig.{ConfigReader, ConfigSource}

import scala.annotation.unused

object ClientConfigParserUtil {

  private case class ProxyWrapper(proxy: Proxy)

  private case class ClientConfigWithoutProxy(listenIp: IPAddress = DefaultAddress,
                                              listenPort: Port = DefaultSocks5Port,
                                              systemProxy: Boolean = false,
                                              rule: Rule,
                                              logLevel: Level = Level.INFO,
                                              testMode: Boolean = false) {

    def toClientConfig(proxyWrapper: ProxyWrapper): ClientConfig = {
      ClientConfig(listenIp, listenPort, systemProxy, proxyWrapper.proxy, rule, logLevel, testMode)
    }
  }

  def parse(config: Path): Result[ClientConfig] = {
    import pureconfig.generic.auto._
    import cl.monsoon.star.client.config.ClientConfigReader._
    val configObjectSource = ConfigSource.file(config)
    val proxyWrapperEither = configObjectSource.load[ProxyWrapper]

    proxyWrapperEither.flatMap { proxyWrapper =>
      // incorrect unused warning from Scalac
      @unused implicit val ruleReader: ConfigReader[Rule] = ClientConfigReader.ruleReader(proxyWrapper.proxy)
      import cl.monsoon.star.config.CommonConfigReader._

      val configWithoutProxyEither = configObjectSource.load[ClientConfigWithoutProxy]
      configWithoutProxyEither.map(_.toClientConfig(proxyWrapper))
    }
  }
}
