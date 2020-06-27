package cl.monsoon.star.client.data

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.IPAddress

object PrivateIpCidrCollector extends Collector[IPAddress] {

  override val resPath = "/data/private_ip_cidr_list.txt"
  override val validator: String => IPAddress = IpAddressUtil.toIpOrCidr
}
