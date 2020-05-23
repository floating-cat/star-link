package cl.monsoon.star.client.data

import java.nio.file.{Files, Paths}

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.HostName

import scala.io.Source
import scala.util.Using

object CnDomainSuffixCollector {

  private val outputPath = Paths.get("data/cn_domain_suffix_list.txt")
  lazy val cnSuffixList: List[HostName] = read()

  def main(args: Array[String]): Unit = {
    val text = Source.fromURL("https://raw.githubusercontent.com/felixonmars/dnsmasq-china-list/master/accelerated-domains.china.conf")
    val domainSuffixLists = text.getLines()
      .filter(x => x.nonEmpty && !x.startsWith("#"))
      .mkString("\n")
      .replace("server=/", "")
      .replace("/114.114.114.114", "")

    Files.writeString(outputPath, domainSuffixLists)
  }

  @throws[Exception]
  private def read(): List[HostName] = {
    Using.resource(Source.fromFile(outputPath.toFile)) { source =>
      source.getLines().map(IpAddressUtil.toDomainName).toList
    }
  }
}
