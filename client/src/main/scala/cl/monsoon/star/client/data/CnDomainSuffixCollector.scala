package cl.monsoon.star.client.data

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.HostName

import scala.io.Source

object CnDomainSuffixCollector extends Collector[HostName] {

  override val resPath = "/data/cn_domain_suffix_list.txt"
  override val validator: String => HostName = IpAddressUtil.toDomainName

  def main(args: Array[String]): Unit = {
    val text = Source.fromURL("https://raw.githubusercontent.com/felixonmars/dnsmasq-china-list/master/accelerated-domains.china.conf")
    val domainSuffixLists = text.getLines()
      .filter(x => x.nonEmpty && !x.startsWith("#"))
      .map(_.replace("server=/", "").replace("/114.114.114.114", ""))
      .tapEach(IpAddressUtil.toDomainName)

    write(args.headOption, domainSuffixLists)
  }
}
