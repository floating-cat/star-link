package cl.monsoon.star.client.data

import java.nio.file.{Files, Paths}

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.HostName

import scala.io.Source
import scala.util.Using

object CnDomainSuffixCollector {

  // Paths#get doesn't work for jar.
  private val outputUrl = getClass.getResource("/data/cn_domain_suffix_list.txt")

  def main(args: Array[String]): Unit = {
    val text = Source.fromURL("https://raw.githubusercontent.com/felixonmars/dnsmasq-china-list/master/accelerated-domains.china.conf")
    val domainSuffixLists = text.getLines()
      .filter(x => x.nonEmpty && !x.startsWith("#"))
      .map(_.replace("server=/", "").replace("/114.114.114.114", ""))
      .tapEach(IpAddressUtil.toDomainName)
      .mkString("\n")

    Files.writeString(Paths.get(outputUrl.toURI), domainSuffixLists)
  }

  def cnSuffixList(): List[HostName] = {
    Using.resource(Source.fromURL(outputUrl)) { source =>
      source.getLines().map(IpAddressUtil.toDomainName).toList
    }
  }
}
