package cl.monsoon.star.client.data

import java.nio.file.{Files, Paths}

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.IPAddressString

import scala.io.Source
import scala.util.Using

object CnIpCidrCollector {

  private val outputPath = Paths.get("data/cn_ip_cidr_list.txt")
  lazy val ipCidrList: List[IPAddressString] = read()

  def main(args: Array[String]): Unit = {
    val text = Source.fromURL("https://raw.githubusercontent.com/17mon/china_ip_list/master/china_ip_list.txt")
    val ipCidrList = text.getLines()
      .filter(x => x.nonEmpty && !x.startsWith("#"))
      .tapEach(IpAddressUtil.toIpOrCidr)
      .mkString("\n")

    Files.writeString(outputPath, ipCidrList)
  }

  @throws[Exception]
  private def read(): List[IPAddressString] = {
    Using.resource(Source.fromFile(outputPath.toFile)) { source =>
      source.getLines().map(IpAddressUtil.toIpOrCidr).toList
    }
  }
}
