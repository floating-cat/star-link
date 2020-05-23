package cl.monsoon.star.client.config

import java.nio.file.Path

import cl.monsoon.star.client.config.ClientConfig.{DefaultAddress, DefaultSocks5Port}
import cl.monsoon.star.config.Port
import inet.ipaddr.IPAddress
import pureconfig.ConfigReader.Result
import pureconfig.{ConfigReader, ConfigSource}

object ClientConfigParserUtil {

  private  case class ProxyWrapper(proxy: Proxy)

  private case class ClientConfigWithoutProxy(listenIp: IPAddress = DefaultAddress, listenPort: Port = DefaultSocks5Port,
                                            rule: Rule, testMode: Boolean = false)

  def parse(config: Path): Result[ClientConfig] = {
    import pureconfig.generic.auto._
    import cl.monsoon.star.config.CommonConfigReader._
    import cl.monsoon.star.client.config.ClientConfigReader._
    val configObjectSource = ConfigSource.file(config)
    val proxyWrapperEither = configObjectSource.load[ProxyWrapper]

    proxyWrapperEither.flatMap { proxyWrapper =>
      implicit val ruleReader: ConfigReader[Rule] = ClientConfigReader.ruleReader(proxyWrapper.proxy)

      val configWithoutProxyEither = configObjectSource.load[ClientConfigWithoutProxy]
      configWithoutProxyEither.map(c =>
        ClientConfig(c.listenIp, c.listenPort, proxyWrapper.proxy, c.rule, c.testMode))
    }
  }
}