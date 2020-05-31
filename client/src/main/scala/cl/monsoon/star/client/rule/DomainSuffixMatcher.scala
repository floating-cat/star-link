package cl.monsoon.star.client.rule

import java.util

import inet.ipaddr.HostName

final class DomainSuffixMatcher(domainSuffixList: List[HostName]) {

  private val domainSuffixMap: util.IdentityHashMap[String, DomainSegment] =
    toMap(domainSuffixList)

  def `match`(domain: HostName): Boolean = {
    @scala.annotation.tailrec
    def loop(remainDomainSegments: collection.Seq[String],
             remainDomainSuffixMap: util.IdentityHashMap[String, DomainSegment]): Boolean = {
      remainDomainSegments match {
        case collection.Seq(x, xs@_*) =>
          remainDomainSuffixMap.get(x.intern()) match {
            case NextSegment(map) => loop(xs, map)
            case EndSegment => true
            case _ => false
          }
        case _ =>
          false
      }
    }

    loop(domain.toNormalizedString.split('.').reverse, domainSuffixMap)
  }

  private def toMap(domainSuffixList: List[HostName]): util.IdentityHashMap[String, DomainSegment] = {
    @scala.annotation.tailrec
    def loop(remainDomainSuffixList: collection.Seq[String],
             nextDomainSuffixMap: util.IdentityHashMap[String, DomainSegment]): DomainSegment = {
      remainDomainSuffixList match {
        case x +: xs =>
          val nextSegment = nextDomainSuffixMap.computeIfAbsent(x.intern(),
            _ => NextSegment(new util.IdentityHashMap[String, DomainSegment]()))
          nextSegment match {
            case NextSegment(map) =>
              loop(xs, map)
            case _ =>
              EndSegment
          }
        case _ => NextSegment(nextDomainSuffixMap)
      }
    }

    val domainSuffixMap = new util.IdentityHashMap[String, DomainSegment]()
    domainSuffixList.foreach { hostName =>
      val hostSegments = hostName.toNormalizedString.split('.')
      val last = hostSegments.head
      val lastSegment = loop(hostSegments.tail.reverse, domainSuffixMap)
      lastSegment match {
        case NextSegment(map) =>
          map.putIfAbsent(last.intern(), EndSegment)
        case _ =>
      }
    }

    domainSuffixMap
  }
}

sealed trait DomainSegment

case object EndSegment extends DomainSegment

final case class NextSegment(map: util.IdentityHashMap[String, DomainSegment]) extends DomainSegment
