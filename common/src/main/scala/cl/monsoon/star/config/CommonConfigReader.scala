package cl.monsoon.star.config

import inet.ipaddr.IPAddress
import pureconfig.{ConfigReader, ConvertHelpers}

object CommonConfigReader {

  implicit val portReader: ConfigReader[Port] =
    ConfigReader[Int].emap(CommonConfigResultUtil.toPort)

  implicit val ipAddrReader: ConfigReader[IPAddress] =
    ConfigReader.fromString[IPAddress](
      ConvertHelpers.catchReadError(s => IpAddressUtil.toIpAddress(s)))

  implicit val passwordReader: ConfigReader[Password] =
    ConfigReader[String].emap(CommonConfigResultUtil.toPassword)
}
