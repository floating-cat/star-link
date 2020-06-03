package cl.monsoon.star.client.data

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.IPAddress

import scala.io.Source
import scala.util.Using

object PrivateIpCidrCollector {

  private val privateIpCidrUrl = getClass.getResource("/data/private_ip_cidr_list.txt")

  def privateIpCidrList(): List[IPAddress] = {
    Using.resource(Source.fromURL(privateIpCidrUrl)) { source =>
      source.getLines().map(IpAddressUtil.toIpOrCidr).toList
    }
  }
}
