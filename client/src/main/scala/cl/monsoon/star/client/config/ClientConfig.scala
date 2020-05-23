package cl.monsoon.star.client.config

import java.net.InetSocketAddress

import cl.monsoon.star.client.config.ClientConfig.{DefaultAddress, DefaultSocks5Port}
import cl.monsoon.star.config.{IpAddressUtil, Password, Port}
import inet.ipaddr.{HostName, IPAddress, IPAddressString}

import scala.collection.immutable.Map

final case class ClientConfig(listenIp: IPAddress = DefaultAddress, listenPort: Port = DefaultSocks5Port,
                              proxy: Proxy, rule: Rule,
                              testMode: Boolean = false) {
  def toSocks5InetSocketAddress: InetSocketAddress =
    new InetSocketAddress(listenIp.toInetAddress, listenPort.value)
}

object ClientConfig {
  val DefaultSocks5Port: Port = Port(1080).getOrElse(throw new Exception)
  val DefaultAddress: IPAddress = IpAddressUtil.toIpAddress("127.0.0.1")
}

final case class Proxy(server: Map[ProxyTag, ServerInfo], default: ProxyTag)

final case class ServerInfo(hostname: HostName, password: Password)

final case class Rule(outRuleSets: Map[OutTag, RuleSet], `final`: OutTag)

sealed class OutTag

case object DefaultProxyTag extends OutTag

case object DirectTag extends OutTag

case object DropTag extends OutTag

final case class ProxyTag private(tag: String) extends OutTag

object OutTag {

  def apply(tag: String, proxyTags: List[ProxyTag]): Either[String, OutTag] = {
    tag match {
      case "proxy" => Right(DefaultProxyTag)
      case "direct" => Right(DirectTag)
      case "drop" => Right(DropTag)
      case s =>
        proxyTags.find(_.tag == s)
          .toRight(s"The '$s' tag doesn't exist. This tag name need to be proxy, direct, drop or " +
            "any tag name that contained in the proxy object")
    }
  }
}

object ProxyTag {

  def apply(tag: String): Either[String, ProxyTag] = {
    Either.cond(tag != "proxy" && tag != "direct" && tag != "drop",
      new ProxyTag(tag),
      "The 'proxy', 'direct' and 'drop' tag names are reserved. Please use other tag names")
  }
}

final case class RuleSet(domainSuffixRules: List[HostName], ipCidr: List[IPAddressString])
