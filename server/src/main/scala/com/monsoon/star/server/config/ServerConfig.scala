package com.monsoon.star.server.config

import com.monsoon.star.config.{IpAddressUtil, Password, Port}
import com.monsoon.star.server.config.ServerConfig.{DefaultAddress, DefaultPort}
import inet.ipaddr.IPAddress

final case class ServerConfig(listenPort: Port = DefaultPort, listenIp: IPAddress = DefaultAddress,
                              password: Password, devMode: Boolean = false)

object ServerConfig {
  val DefaultPort: Port = Port(1200).getOrElse(throw new Exception)
  val DefaultAddress: IPAddress = IpAddressUtil.toIpAddress("127.0.0.1")
}
