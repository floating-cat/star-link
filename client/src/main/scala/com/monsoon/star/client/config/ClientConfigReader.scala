package com.monsoon.star.client.config

import pureconfig.ConfigReader
import pureconfig.ConfigReader.Result
import pureconfig.error.{ConfigReaderFailures, FailureReason}

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.util.chaining._

object ClientConfigReader {

  implicit val stringTagReader: ConfigReader[StringTag] = {
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
            .map(_.asString.map(StringTag(_).getOrElse(throw new Exception)))
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

  private def sequence[A, B](map: Map[Result[A], Result[B]]): ConfigReader.Result[Map[A, B]] =
    map.foldLeft(Right(Map.newBuilder[A, B]): ConfigReader.Result[mutable.Builder[(A, B), Map[A, B]]]) {
      case (Right(builder), (Right(a), Right(b))) => Right(builder.addOne(a, b))
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

  final case class UnmatchedProxyDefault(defaultTag: StringTag) extends FailureReason {
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
