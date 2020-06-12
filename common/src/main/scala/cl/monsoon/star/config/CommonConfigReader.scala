package cl.monsoon.star.config

import inet.ipaddr.IPAddress
import org.apache.logging.log4j.Level
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

object CommonConfigReader {

  implicit val portReader: ConfigReader[Port] =
    ConfigReader[Int].emap(CommonConfigResultUtil.toPort)

  implicit val ipAddrReader: ConfigReader[IPAddress] =
    ConfigReader[String].emap(
      CommonConfigResultUtil.catchReadError0(s => IpAddressUtil.toIpAddress(s), "IP"))

  implicit val passwordReader: ConfigReader[Password] =
    ConfigReader[String].emap(CommonConfigResultUtil.toPassword)

  implicit val logLevel: ConfigReader[Level] =
    ConfigReader[String].emap { s =>
      val level = Level.getLevel(s.toUpperCase)
      if (level != null) Right(level)
      else Left(CannotConvert(s,
        "log level",
        s"You need to use one of the following levels: ${Level.values().map(_.name().toLowerCase).mkString(" ")}"))
    }
}
