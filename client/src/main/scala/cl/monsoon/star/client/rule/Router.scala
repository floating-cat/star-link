package cl.monsoon.star.client.rule

import cl.monsoon.star.client.config._
import cl.monsoon.star.config.IpAddressUtil
import grizzled.slf4j.Logger
import inet.ipaddr.{HostName, IPAddress}

import scala.util.{Failure, Success, Try}

final class Router(rule: Rule) {

  private val logger = Logger[this.type]

  private val priority: List[RuleTag] = getTagPriority(rule)
  // TODO in future Scala version
  private val domainSuffixMatcher = rule.sets.view.mapValues(ruleSet =>
    (new DomainSuffixMatcher(ruleSet.domainSuffixList), new IpCidrMatcher(ruleSet.ipCidrs)))
    .toMap
  private val finalNodeTag = rule.`final`
  private type RuleMatcher = (DomainSuffixMatcher, IpCidrMatcher)

  private val domainSuffixMather: (HostName, RuleMatcher) => Boolean =
    (hostname, ruleMatcher) => ruleMatcher._1.`match`(hostname)
  private val ipCidrMather: (IPAddress, RuleMatcher) => Boolean =
    (iPAddress, ruleMatcher) => ruleMatcher._2.`match`(iPAddress)

  def decide(address: String): RouteResult = {
    Try(IpAddressUtil.toHostNameWithoutPort(address)) match {
      case Success(hostName) =>
        if (hostName.isAddress) {
          `match`(hostName.toAddress)(ipCidrMather)
        } else {
          // else domain
          `match`(hostName)(domainSuffixMather)
        }

      case Failure(e) =>
        logger.warn(s"Incorrect host name or IP: $address", e)
        RejectRouteResult
    }
  }

  private def getTagPriority(rule: Rule): List[RuleTag] = {
    val tags = rule.sets.keys
    val (proxyTags, otherTags) = tags.partition(_.isInstanceOf[ProxyTag])
    val defaultProxyTag = otherTags.find(_ == DefaultProxyTag)
    val directTag = otherTags.find(_ == DirectTag)
    val rejectTag = otherTags.find(_ == RejectTag)
    (proxyTags ++ defaultProxyTag ++ directTag ++ rejectTag).toList
  }

  private def `match`[A](a: A)(matcher: (A, RuleMatcher) => Boolean): RouteResult = {
    @scala.annotation.tailrec
    def loop(xs: List[RuleTag]): RuleTag = {
      xs match {
        case x :: xs =>
          val ruleMatcher = domainSuffixMatcher(x)
          if (matcher(a, ruleMatcher)) x
          else loop(xs)
        case _ =>
          finalNodeTag
      }
    }

    loop(priority) match {
      case t@ProxyTag(_) => ProxyRouteResult(t)
      case DefaultProxyTag => DefaultProxyRouteResult
      case DirectTag => DirectRouteResult
      case RejectTag => RejectRouteResult
    }
  }
}

sealed trait RouteResult

case object DefaultProxyRouteResult extends RouteResult

final case class ProxyRouteResult(tag: ProxyTag) extends RouteResult

case object DirectRouteResult extends RouteResult

case object RejectRouteResult extends RouteResult
