package com.monsoon.star.server.config

import com.monsoon.star.config.{IpAddressUtil, Password, Port}
import com.monsoon.star.server.config.ServerConfig.{DefaultAddress, DefaultPort}
import inet.ipaddr.IPAddress

final case class ServerConfig(general: General)

final case class General(port: Port = DefaultPort, listenIp: IPAddress = DefaultAddress,
                         password: Password)

object ServerConfig {
  val DefaultPort: Port = Port(1100).getOrElse(throw new Exception)
  val DefaultAddress: IPAddress = IpAddressUtil.toIpAddress("127.0.0.1")
}
