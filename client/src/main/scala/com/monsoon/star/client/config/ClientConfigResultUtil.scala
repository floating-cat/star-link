package com.monsoon.star.client.config

import com.monsoon.star.config.CommonConfigResultUtil.{toPassword, toPort}
import com.monsoon.star.config.IpAddressUtil
import inet.ipaddr.HostName
import pureconfig.error.{CannotConvert, FailureReason}

import scala.util.Try

object ClientConfigResultUtil {

  def toStringTag(tag: String): Either[FailureReason, StringTag] = {
    StringTag(tag)
      .left.map(CannotConvert(tag, "proxy tag", _))
  }

  def toServerInfo(str: String): Either[FailureReason, ServerInfo] = {
    val split = str.split("\\s+")
    Either.cond(
      split.size >= 3,
      toServerInfo(split(0), split(1), split(2)),
      CannotConvert(str, "server info", "The server address, port, and password " +
        s"need to be separated by whitespaces, " +
        "e.g., www.example.com 443 b32c4ce79792d991bf75f2d47cf56cbd"))
      .flatten
  }

  private def toServerInfo(address: String, port: String, password: String): Either[FailureReason, ServerInfo] =
    toHostName(address, port)
      .flatMap(hostName => {
        toPassword(password)
          .map(ServerInfo(hostName, _))
      })

  private def toHostName(address: String, port: String): Either[FailureReason, HostName] = {
    Try(IpAddressUtil.toHostNameWithoutPort(address))
      .toEither
      .left.map(err => CannotConvert(address, "host name", err.toString))
      .flatMap(hostName =>
        toPort(port)
          .map(p => new HostName(hostName.asAddress(), p.value))
      )
  }
}
