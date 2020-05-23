package cl.monsoon.star.client.config

import cl.monsoon.star.config.{CommonConfigResultUtil, IpAddressUtil}
import pureconfig.ConfigReader.Result
import pureconfig.error.{ConfigReaderFailure, ConfigReaderFailures, FailureReason}
import pureconfig.{ConfigCursor, ConfigReader}

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.chaining._

object ClientConfigReader {

  implicit val stringTagReader: ConfigReader[ProxyTag] = {
    ConfigReader[String].emap(tag => ClientConfigResultUtil.toStringTag(tag))
  }

  implicit val proxyReader: ConfigReader[Proxy] =
    ConfigReader.fromCursor[Proxy] { cur =>
      cur.asObjectCursor.flatMap { objCur =>
        val defaultKey = "default"
        val proxyMap = objCur.map.removed(defaultKey)
          .map { case (key, value) =>
            (objCur.atKeyOrUndefined(key).scopeFailure(ClientConfigResultUtil.toStringTag(key)),
              value.asString.flatMap(s => value.scopeFailure(ClientConfigResultUtil.toServerInfo(s))))
          }
          .pipe(sequence)
          .flatMap { map =>
            Either.cond(map.nonEmpty, map, ConfigReaderFailures(cur.failureFor(ProxyInfoNotFound)))
          }

        proxyMap.flatMap { map =>
          objCur.map
            .get(defaultKey)
            .map(_.asString.map(ProxyTag(_).getOrElse(throw new Exception)))
            .orElse(Option.when(map.size == 1)(Right(map.head._1)))
            .getOrElse(cur.failed(NoDefaultProxyInfo))
            .flatMap {
              stringTag =>
                Either.cond(map.contains(stringTag), stringTag,
                  ConfigReaderFailures(cur.failureFor(UnmatchedProxyDefault(stringTag))))
            }
            .map(Proxy(map, _))
        }
      }
    }

  def ruleReader(proxy: Proxy): ConfigReader[Rule] = {
    ConfigReader.fromCursor[Rule] { cur =>
      cur.asObjectCursor.flatMap { objCur => {
        val finalKey = "final"
        val proxyTags = proxy.server.keys.toList

        val rulesetMapEither = objCur.map.removed(finalKey).map { case (key, value) =>
          (objCur.atKeyOrUndefined(key).scopeFailure(ClientConfigResultUtil.toRuleTag(key, proxyTags)),
            toRuleSetResult(value))
        }.pipe(sequence)

        val finalNodeTagEither = objCur.atKey(finalKey) match {
          case Left(_) => Right(DefaultProxyTag)
          case Right(finalCur) =>
            finalCur.asString.flatMap { finalNodeTag =>
              finalCur.scopeFailure(ClientConfigResultUtil.toRuleTag(finalNodeTag, proxyTags))
            }
        }
        (rulesetMapEither, finalNodeTagEither) match {
          case (Right(ruleSetMap), Right(finalNodeTag)) => Right(Rule(ruleSetMap, finalNodeTag))
          case (Left(errs), Left(errrs)) => Left(errs ++ errrs)
          case (Left(errs), _) => Left(errs)
          case (_, Left(errs)) => Left(errs)
        }
      }
      }
    }
  }

  private def toRuleSetResult(configCursor: ConfigCursor): Result[RuleSet] = {
    val domainSuffixResult = parseListAt("domain-suffix", configCursor, IpAddressUtil.toDomainName, "domain")
    val ipCidrResult = parseListAt("ip-cidr", configCursor, IpAddressUtil.toIpOrCidr, "IP or Cidr")

    (domainSuffixResult, ipCidrResult) match {
      case (Right(r), Right(rr)) => r.flatMap(rs => rr.map(RuleSet(rs, _)))
      case (Left(_), Right(r)) => r.map(RuleSet(List.empty, _))
      case (Right(r), Left(_)) => r.map(RuleSet(_, List.empty))
      case (Left(_), Left(_)) => Right(RuleSet(List.empty, List.empty))
    }
  }

  private def parseListAt[A](path: String, configCursor: ConfigCursor, f: String => A, toType: String): Result[Result[List[A]]] = {
    configCursor.fluent.at(path).asListCursor
      .map { configListCursor =>
        configListCursor.list.foldLeft[Either[ListBuffer[ConfigReaderFailure], ListBuffer[A]]](
          Right(ListBuffer.empty)) { (hostsEither, cur) =>
          val hostEither = cur.asString.flatMap { domain =>
            cur.scopeFailure(CommonConfigResultUtil.catchReadError0(f, toType).apply(domain))
          }

          (hostsEither, hostEither) match {
            case (Right(hosts), Right(host)) => Right(hosts += host)
            case (Left(errs), Left(err)) => Left(concat(errs, err))
            case (Left(errs), _) => Left(errs)
            case (_, Left(err)) => Left(concat0(err))
          }
        }.map(_.result())
          .left.map(buf => new ConfigReaderFailures(buf.head, buf.tail.result()))
      }
  }

  private def sequence[A, B](map: Map[Result[A], Result[B]]): Result[Map[A, B]] =
    map.foldLeft[Either[mutable.ListBuffer[ConfigReaderFailure], mutable.Builder[(A, B), Map[A, B]]]](
      Right(Map.newBuilder)) {
      case (Right(builder), (Right(key), Right(value))) => Right(builder += ((key, value)))
      case (Left(errs), (Left(err), Left(errr))) => Left(concat(errs, err, errr))
      case (Left(errs), (Left(err), _)) => Left(concat(errs, err))
      case (Left(errs), (_, Left(err))) => Left(concat(errs, err))
      case (Left(errs), _) => Left(errs)
      case (_, (Left(err), Left(errr))) => Left(concat0(err, errr))
      case (_, (Left(err), _)) => Left(concat0(err))
      case (_, (_, Left(err))) => Left(concat0(err))
    }.map(_.result())
      .left.map(buf => new ConfigReaderFailures(buf.head, buf.tail.result()))

  private def concat(buf: ListBuffer[ConfigReaderFailure], errs: ConfigReaderFailures*): ListBuffer[ConfigReaderFailure] = {
    errs.foreach(err => buf += err.head ++= err.tail)
    buf
  }

  private def concat0(errs: ConfigReaderFailures*): ListBuffer[ConfigReaderFailure] =
    concat(ListBuffer.empty, errs: _*)

  private object ProxyInfoNotFound extends FailureReason {
    override def description: String =
      """No proxy node is found in the proxy object.
        |Example:
        |proxy {
        |  node_name = example.com 1100 b32c4ce79792d991bf75f2d47cf56cbd
        |}""".stripMargin
  }

  private object NoDefaultProxyInfo extends FailureReason {
    override def description: String =
      """No default field is found in the proxy object.
        |If you have more than one proxy node in your profile, you have to add the default field.
        |Example:
        |proxy {
        |  node_name1 = example1.com 1100 b32c4ce79792d991bf75f2d47cf56cbd
        |  node_name2 = example2.com 1101 9df5b42379878286e30e005eaf87958c
        |  default = node_name1
        |}""".stripMargin
  }

  final case class UnmatchedProxyDefault(defaultTag: ProxyTag) extends FailureReason {
    override def description: String =
      s"""No corresponding proxy node '${defaultTag.tag}' is found in the proxy object.
         |Example:
         |proxy {
         |  # You need to have a proxy node named ${defaultTag.tag} in this proxy object
         |  ${defaultTag.tag} = example.com 1100 b32c4ce79792d991bf75f2d47cf56cbd
         |  default = ${defaultTag.tag}
         |}""".stripMargin
  }

}
