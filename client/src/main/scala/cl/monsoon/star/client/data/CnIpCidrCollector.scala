package cl.monsoon.star.client.data

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.IPAddress

import scala.io.Source

object CnIpCidrCollector extends Collector[IPAddress] {

  override val resPath = "/data/cn_ip_cidr_list.txt"
  override val validator: String => IPAddress = IpAddressUtil.toIpOrCidr

  def main(args: Array[String]): Unit = {
    val text = Source.fromURL("https://raw.githubusercontent.com/17mon/china_ip_list/master/china_ip_list.txt")
    val ipCidrList = text.getLines()
      .filter(x => x.nonEmpty && !x.startsWith("#"))
      .tapEach(IpAddressUtil.toIpOrCidr)

    write(args.headOption, ipCidrList)
  }
}
