package cl.monsoon.star.client.rule

import cl.monsoon.star.client.config._
import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.{HostName, IPAddressString}
import io.netty.handler.codec.socksx.v5.Socks5AddressType._
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

// TODO
object Router {

  def decide(commandRequest: Socks5CommandRequest, rule: Rule): RouteResult = {
    val hostname = IpAddressUtil.toHostNameWithoutPort(commandRequest.dstAddr())

    val addrType = commandRequest.dstAddrType()
    val priorityList = getTagPriority(rule)
    if (addrType == DOMAIN) {
      `match`(priorityList, rule) { matcher =>
        matchDomainSuffixList(hostname, matcher.domainSuffixList)
      }
    } else if (addrType == IPv4 || addrType == IPv6) {
      `match`(priorityList, rule) { matcher =>
        matchIpCidrList(hostname.asAddressString(), matcher.ipCidrs)
      }
    } else {
      // TODO
      throw new NotImplementedError()
    }
  }

  private def getTagPriority(rule: Rule): List[RuleTag] = {
    val tags = rule.sets.keys
    val (proxyTags, otherTags) = tags.span(_.isInstanceOf[ProxyTag])
    val defaultProxyTag = otherTags.find(_ == DefaultProxyTag)
    val directTag = otherTags.find(_ == DirectTag)
    val rejectTag = otherTags.find(_ == RejectTag)
    (proxyTags ++ defaultProxyTag ++ directTag ++ rejectTag).toList
  }

  private def `match`(priorityList: List[RuleTag], rule: Rule)(matcher: RuleSet => Boolean): RouteResult = {
    @scala.annotation.tailrec
    def loop(xs: List[RuleTag]): RuleTag = {
      xs match {
        case x :: xs =>
          val ruleset = rule.sets(x)
          if (matcher(ruleset)) x
          else loop(xs)
        case _ =>
          rule.`final`
      }
    }

    loop(priorityList) match {
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
