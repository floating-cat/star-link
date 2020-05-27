package cl.monsoon.star.client.rule

import cl.monsoon.star.client.config._
import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.{HostName, IPAddressString}
import io.netty.handler.codec.socksx.v5.Socks5AddressType._
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

import scala.util.{Failure, Success, Try}

// TODO
final class Router(rule: Rule) {

  private val priority: List[RuleTag] = getTagPriority

  private val domainSuffixMather: (HostName, RuleSet) => Boolean =
    (hostname, ruleset) => matchDomainSuffixList(hostname, ruleset.domainSuffixList)
  private val ipCidrMather: (IPAddressString, RuleSet) => Boolean =
    (iPAddressString, ruleset) => matchIpCidrList(iPAddressString, ruleset.ipCidrs)

  def decide(commandRequest: Socks5CommandRequest): RouteResult = {
    Try(IpAddressUtil.toHostNameWithoutPort(commandRequest.dstAddr())) match {
      case Success(hostName) =>
        val addrType = commandRequest.dstAddrType()
        if (addrType == DOMAIN) {
          `match`(hostName)(domainSuffixMather)
        } else if (addrType == IPv4 || addrType == IPv6) {
          `match`(hostName.asAddressString())(ipCidrMather)
        } else {
          // TODO
          println(s"Unknown address type: ${addrType.byteValue()}.")
          RejectRouteResult
        }

      case Failure(exception) =>
        // TODO
        println(exception)
        RejectRouteResult
    }
  }

  private def getTagPriority: List[RuleTag] = {
    val tags = rule.sets.keys
    val (proxyTags, otherTags) = tags.partition(_.isInstanceOf[ProxyTag])
    val defaultProxyTag = otherTags.find(_ == DefaultProxyTag)
    val directTag = otherTags.find(_ == DirectTag)
    val rejectTag = otherTags.find(_ == RejectTag)
    (proxyTags ++ defaultProxyTag ++ directTag ++ rejectTag).toList
  }

  private def `match`[A](a: A)(matcher: (A, RuleSet) => Boolean): RouteResult = {
    @scala.annotation.tailrec
    def loop(xs: List[RuleTag]): RuleTag = {
      xs match {
        case x :: xs =>
          val ruleset = rule.sets(x)
          if (matcher(a, ruleset)) x
          else loop(xs)
        case _ =>
          rule.`final`
      }
    }

    loop(priority) match {
      case t@ProxyTag(_) => ProxyRouteResult(t)
      case DefaultProxyTag => DefaultProxyRouteResult
      case DirectTag => DirectRouteResult
      case RejectTag => RejectRouteResult
    }
  }

  private def matchDomainSuffixList(domain: HostName, domainSuffixList: List[HostName]): Boolean = {
    val domainNormalized = domain.toNormalizedString
    domainSuffixList.exists { hostName =>
      val domainSuffixNormalized = hostName.toNormalizedString
      domainNormalized.endsWith(s".$domainSuffixNormalized") || domainNormalized == domainSuffixNormalized
    }
  }

  private def matchIpCidrList(ipAddress: IPAddressString, ipCidrs: List[IPAddressString]): Boolean = {
    ipCidrs.exists { ipOrCidr =>
      ipOrCidr.contains(ipAddress)
    }
  }
}

sealed trait RouteResult

case object DefaultProxyRouteResult extends RouteResult

final case class ProxyRouteResult(tag: ProxyTag) extends RouteResult

case object DirectRouteResult extends RouteResult

case object RejectRouteResult extends RouteResult
