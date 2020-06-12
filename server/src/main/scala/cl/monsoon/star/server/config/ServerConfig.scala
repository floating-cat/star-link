package cl.monsoon.star.server.config

import cl.monsoon.star.config.{IpAddressUtil, Password, Port}
import cl.monsoon.star.server.config.ServerConfig.{DefaultAddress, DefaultPort}
import inet.ipaddr.IPAddress
import org.apache.logging.log4j.Level

final case class ServerConfig(listenPort: Port = DefaultPort, listenIp: IPAddress = DefaultAddress,
                              password: Password, logLevel: Level = Level.INFO, testMode: Boolean = false)

object ServerConfig {
  val DefaultPort: Port = Port(1200).getOrElse(throw new Exception)
  val DefaultAddress: IPAddress = IpAddressUtil.toIpAddress("::1")
}
