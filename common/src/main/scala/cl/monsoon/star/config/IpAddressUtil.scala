package cl.monsoon.star.config

import inet.ipaddr._

import scala.util.chaining._

object IpAddressUtil {

  @throws[AddressStringException]
  def toIpAddress(ipAddress: String): IPAddress =
    new IPAddressString(ipAddress, IpAddressUtil.Parameter.IpAddressString)
      .tap(_.validate())
      .getAddress

  @throws[HostNameException]
  def toHostNameWithPort(hostname: String): HostName =
    new HostName(hostname, IpAddressUtil.Parameter.HostNameWithPort)
      .tap(_.validate())

  @throws[HostNameException]
  def toHostNameWithoutPort(hostname: String): HostName =
    new HostName(hostname, IpAddressUtil.Parameter.HostNameWithoutPort)
      .tap(_.validate())

  @throws[HostNameException]
  def toHostNameWithPortOption(hostname: String): HostName =
    new HostName(hostname, IpAddressUtil.Parameter.HostNameWithPortOption)
      .tap(_.validate())

  @throws[HostNameException]
  def toDomainName(domainName: String): HostName =
    new HostName(domainName, IpAddressUtil.Parameter.DomainName)
      .tap(_.validate())

  @throws[AddressStringException]
  def toIpOrCidr(ipOrCidr: String): IPAddress =
    new IPAddressString(ipOrCidr, IpAddressUtil.Parameter.IpOrCidr)
      .tap(_.validate())
      .getAddress

  private object Parameter {

    val IpAddressString: IPAddressStringParameters =
      new IPAddressStringParameters.Builder()
        .allowMask(false)
        .allowPrefix(false)
        .allowPrefixOnly(false)
        .toParams

    val HostNameWithPort: HostNameParameters =
      new HostNameParameters(
        IpAddressString,
        false,
        false,
        true,
        true,
        true,
        true,
        true,
        true,
        false
      )

    val HostNameWithoutPort: HostNameParameters =
      new HostNameParameters(
        IpAddressString,
        false,
        false,
        true,
        true,
        true,
        true,
        false,
        false,
        false
      )

    val HostNameWithPortOption: HostNameParameters =
      new HostNameParameters(
        IpAddressString,
        false,
        false,
        true,
        true,
        true,
        true,
        true,
        false,
        false
      )

    val DomainName: HostNameParameters =
      new HostNameParameters(
        IpAddressString,
        false,
        false,
        false,
        false,
        true,
        false,
        false,
        false,
        false
      )

    val IpOrCidr: IPAddressStringParameters =
      new IPAddressStringParameters.Builder()
        .allowMask(false)
        .allowPrefix(true)
        .allowPrefixOnly(true)
        .toParams
  }

}
