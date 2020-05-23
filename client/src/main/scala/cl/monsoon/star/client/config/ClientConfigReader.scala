package cl.monsoon.star.client.config

import cl.monsoon.star.config.IpAddressUtil
import pureconfig.ConfigReader.Result
import pureconfig.error.{CannotConvert, ConfigReaderFailures, FailureReason}
import pureconfig.{ConfigCursor, ConfigListCursor, ConfigReader, ConvertHelpers}

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.util.chaining._

object ClientConfigReader {

  implicit val stringTagReader: ConfigReader[ProxyTag] = {
    ConfigReader[String].emap(tag => ClientConfigResultUtil.toStringTag(tag))
  }

  implicit val proxyReader: ConfigReader[Proxy] =
    ConfigReader.fromCursor[Proxy] { cur =>
      val defaultKey = "default"
      cur.asObjectCursor.flatMap { objCur =>
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
        val proxyTags = proxy.server.keys.toList
        val finalKey = "final"

        val rulesetMap = objCur.map.removed(finalKey).map { case (key, value) =>
          val keyResult = OutTag(key, proxyTags).left.map(CannotConvert(key, "rule tag", _))
          (objCur.atKeyOrUndefined(key).scopeFailure(keyResult),
            toRuleSetResult(value))
        }.pipe(sequence)

        rulesetMap.map(Rule(_, DefaultProxyTag))
      }
      }
    }
  }

  private def toRuleSetResult(configCursor: ConfigCursor): Result[RuleSet] = {
    val domainSuffixResult = configCursor.fluent.at("domain-suffix").asListCursor
      .map(a => map0(a, IpAddressUtil.toDomainName))
    val ipCidrResult = configCursor.fluent.at("ip-cidr").asListCursor
      .map(a => map0(a, IpAddressUtil.toIpOrCidr))

    (domainSuffixResult, ipCidrResult) match {
      case (Left(_), Right(r)) => r.map(RuleSet(List.empty, _))
      case (Right(r), Left(_)) => r.map(RuleSet(_, List.empty))
      case (Right(r), Right(rr)) => r.flatMap(rs => rr.map(RuleSet(rs, _)))
      case (Left(_), Left(_)) => Right(RuleSet(List.empty, List.empty))
    }
  }

  private def map0[A](configListCursor: ConfigListCursor, f: String => A)
                     (implicit ct: ClassTag[A]): Result[List[A]] = {
    configListCursor.list.foldLeft[Result[ListBuffer[A]]](Right(ListBuffer.empty)) { (hostNamesEither, cur) =>
      val hostEither = cur.asString.flatMap { domain =>
        cur.scopeFailure(ConvertHelpers.catchReadError(f).apply(domain))
      }

      (hostNamesEither, hostEither) match {
        case (Right(xs), Right(x)) => Right(xs.addOne(x))
        case (Left(xs), Left(xss)) => Left(xs ++ xss)
        case (Left(xs), _) => Left(xs)
        case (_, Left(xs)) => Left(xs)
      }
    }.map(_.result())
  }

  private def sequence[A, B](map: Map[Result[A], Result[B]]): Result[Map[A, B]] =
    map.foldLeft(Right(Map.newBuilder[A, B]): Result[mutable.Builder[(A, B), Map[A, B]]]) {
      case (Right(builder), (Right(a), Right(b))) => Right(builder.addOne((a, b)))
      case (Left(errs), (Left(err), _)) => Left(errs ++ err)
      case (Left(errs), (_, Left(err))) => Left(errs ++ err)
      case (Left(errs), _) => Left(errs)
      case (_, (Left(err), _)) => Left(err)
      case (_, (_, Left(err))) => Left(err)
    }.map(_.result())

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
