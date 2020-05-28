package cl.monsoon.star.client.data

import java.nio.file.{Files, Paths}

import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.IPAddress

import scala.io.Source
import scala.util.Using

object CnIpCidrCollector {

  private val outputUrl = ClassLoader.getSystemResource("/data/cn_ip_cidr_list.txt")

  def main(args: Array[String]): Unit = {
    val text = Source.fromURL("https://raw.githubusercontent.com/17mon/china_ip_list/master/china_ip_list.txt")
    val ipCidrList = text.getLines()
      .filter(x => x.nonEmpty && !x.startsWith("#"))
      .tapEach(IpAddressUtil.toIpOrCidr)
      .mkString("\n")

    Files.writeString(Paths.get(outputUrl.toURI), ipCidrList)
  }

  def ipCidrList(): List[IPAddress] = {
    Using.resource(Source.fromURL(outputUrl)) { source =>
      source.getLines().map(IpAddressUtil.toIpOrCidr).toList
    }
  }
}
