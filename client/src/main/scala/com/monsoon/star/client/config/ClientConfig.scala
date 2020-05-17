package com.monsoon.star.client.config

import java.net.InetSocketAddress

import com.monsoon.star.client.config.ClientConfig.{DefaultAddress, DefaultSocks5Port}
import com.monsoon.star.config.{IpAddressUtil, Password, Port}
import inet.ipaddr.{HostName, IPAddress}

import scala.collection.immutable.Map

final case class ClientConfig(listenIp: IPAddress = DefaultAddress, listenPort: Port = DefaultSocks5Port,
                              proxy: Proxy, testMode: Boolean = false) {
  def toSocks5InetSocketAddress: InetSocketAddress =
    new InetSocketAddress(listenIp.toInetAddress, listenPort.value)
}

object ClientConfig {
  val DefaultSocks5Port: Port = Port(1080).getOrElse(throw new Exception)
  val DefaultAddress: IPAddress = IpAddressUtil.toIpAddress("127.0.0.1")
}

final case class Proxy(server: Map[StringTag, ServerInfo], default: StringTag)

final case class ServerInfo(hostname: HostName, password: Password)

final case class Route(`final`: OutTag)

sealed class OutTag

object ProxyTag extends OutTag

object DirectTag extends OutTag

final case class StringTag private(tag: String) extends OutTag

object StringTag {
  def toOption(tag: String): Option[StringTag] = {
    Option.when(tag != "proxy" && tag != "direct")(new StringTag(tag))
  }

  def apply(tag: String): Either[String, StringTag] = {
    Either.cond(tag != "proxy" && tag != "direct",
      new StringTag(tag),
      "The 'proxy' and the 'direct' proxy tag names are reserved. Please use other tag names")
  }
}
