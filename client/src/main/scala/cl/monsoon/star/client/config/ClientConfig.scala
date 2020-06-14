package cl.monsoon.star.client.config

import java.net.InetSocketAddress

import cl.monsoon.star.client.config.ClientConfig.{DefaultAddress, DefaultSocks5Port}
import cl.monsoon.star.config.{IpAddressUtil, Password, Port}
import inet.ipaddr.{HostName, IPAddress}
import org.apache.logging.log4j.Level

import scala.collection.immutable.Map

final case class ClientConfig(listenIp: IPAddress = DefaultAddress,
                              listenPort: Port = DefaultSocks5Port,
                              systemProxy: Boolean = false,
                              proxy: Proxy, rule: Rule,
                              logLevel: Level = Level.INFO,
                              testMode: Boolean = false) {
  def toSocks5InetSocketAddress: InetSocketAddress =
    new InetSocketAddress(listenIp.toInetAddress, listenPort.value)
}

object ClientConfig {
  val DefaultSocks5Port: Port = Port(1080).getOrElse(throw new Exception)
  val DefaultAddress: IPAddress = IpAddressUtil.toIpAddress("::1")
}

final case class Proxy(server: Map[ProxyTag, ServerInfo], default: ProxyTag)

final case class ServerInfo(hostname: HostName, password: Password)

final case class Rule(sets: Map[RuleTag, RuleSet], `final`: RuleTag)

sealed trait RuleTag

final case class ProxyTag private(tag: String) extends RuleTag

case object DefaultProxyTag extends RuleTag

case object DirectTag extends RuleTag

case object RejectTag extends RuleTag

object RuleTag {

  def apply(tag: String, proxyTags: List[ProxyTag]): Either[String, RuleTag] = {
    tag match {
      case "proxy" => Right(DefaultProxyTag)
      case "direct" => Right(DirectTag)
      case "reject" => Right(RejectTag)
      case s =>
        proxyTags.find(_.tag == s)
          .toRight(s"The '$s' tag doesn't exist. This tag name need to be proxy, direct, reject or " +
            "any tag name that contained in the proxy object")
    }
  }
}

object ProxyTag {

  def apply(tag: String): Either[String, ProxyTag] = {
    Either.cond(tag != "proxy" && tag != "direct" && tag != "reject",
      new ProxyTag(tag),
      "The 'proxy', 'direct' and 'reject' tag names are reserved. Please use other tag names")
  }
}

final case class RuleSet(domainSuffixList: List[HostName], ipCidrs: List[IPAddress])
