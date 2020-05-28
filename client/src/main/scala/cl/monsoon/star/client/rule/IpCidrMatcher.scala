package cl.monsoon.star.client.rule

import java.math.BigInteger

import inet.ipaddr.IPAddress

import scala.collection.Searching.Found

final class IpCidrMatcher(ipCidrList: List[IPAddress]) {

  private val (ipv4CidrsBound, ipv6CidrsBound) = toArrayPair(ipCidrList)

  def `match`(ipAddress: IPAddress): Boolean = {
    if (ipAddress.isIPv4) {
      ipv4CidrsBound.search((ipAddress.toIPv4.intValue(), 0))(Ipv4Ordering).isInstanceOf[Found]
    } else {
      ipv6CidrsBound.search((ipAddress.getValue, BigInteger.ZERO))(Ipv6Ordering).isInstanceOf[Found]
    }
  }

  private def toArrayPair(ipCidrList: List[IPAddress]): (Array[(Int, Int)], Array[(BigInteger, BigInteger)]) = {
    val (ipv4Cidr, ipv6Cidr) = ipCidrList.partition(_.isIPv4)

    val ipv4Cidrs = ipv4Cidr.view.map(ipCidr => {
      val ipv4 = ipCidr.toIPv4
      (ipv4.intValue(), ipv4.upperIntValue())
    }).toArray.sorted

    val ipv6Cidrs = ipv6Cidr.view.map(ipv6Cidr => {
      (ipv6Cidr.getValue, ipv6Cidr.getUpperValue)
    }).toArray.sorted

    (ipv4Cidrs, ipv6Cidrs)
  }


  object Ipv4Ordering extends Ordering[(Int, Int)] {
    override def compare(x: (Int, Int), y: (Int, Int)): Int = {
      val ip = x._1
      val (lower, upper) = y
      if (ip < lower) {
        -1
      } else if (ip > upper) {
        1
      } else {
        0
      }
    }
  }

  object Ipv6Ordering extends Ordering[(BigInteger, BigInteger)] {
    override def compare(x: (BigInteger, BigInteger), y: (BigInteger, BigInteger)): Int = {
      val ip = x._1
      val (lower, upper) = y
      if (ip.compareTo(lower) < 0) {
        -1
      } else if (ip.compareTo(upper) > 0) {
        1
      } else {
        0
      }
    }
  }

}
