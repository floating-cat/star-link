package cl.monsoon.star.config

import inet.ipaddr.IPAddress
import pureconfig.ConfigReader

object CommonConfigReader {

  implicit val portReader: ConfigReader[Port] =
    ConfigReader[Int].emap(CommonConfigResultUtil.toPort)

  implicit val ipAddrReader: ConfigReader[IPAddress] =
    ConfigReader.fromString[IPAddress](
      CommonConfigResultUtil.catchReadError0(s => IpAddressUtil.toIpAddress(s), "IP"))

  implicit val passwordReader: ConfigReader[Password] =
    ConfigReader[String].emap(CommonConfigResultUtil.toPassword)
}
