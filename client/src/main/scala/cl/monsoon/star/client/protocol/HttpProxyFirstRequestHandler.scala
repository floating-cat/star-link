package cl.monsoon.star.client.protocol

import java.net.InetSocketAddress

import cl.monsoon.star._
import cl.monsoon.star.client.protocol.CommandRequest.{HttpConnect, HttpProxy, HttpProxyDumb}
import cl.monsoon.star.client.protocol.HttpProxyFirstRequestHandler.{HttpProxyBuilder, requestLineRegex}
import cl.monsoon.star.config.IpAddressUtil
import inet.ipaddr.HostName
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpMethod, HttpVersion}
import io.netty.util.ReferenceCountUtil

import scala.util.Try
import scala.util.matching.Regex

final class HttpProxyFirstRequestHandler extends BaseChannelInboundHandlerAdapter {

  private val headerParser = new HeaderParser()
  private var skipToEnd = false
  private var httpConnectBuilder: HttpProxyBuilder = _

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val buf = msg.asInstanceOf[ByteBuf]

    @scala.annotation.tailrec
    def loopToSkipToEnd(): Unit = {
      headerParser.parse(buf) match {
        case Value(_) =>
          loopToSkipToEnd()

        case End =>
          buf.release()
          ctx.fireChannelRead(httpConnectBuilder.toConnect)
          ctx.pipeline().remove(this)

        case Suspension =>
      }
    }

    if (!skipToEnd) {
      headerParser.parse(buf) match {
        case Value(v) =>
          toHttpProxyRequest(v.toString) match {
            case Right(httpProxyBuilder) =>
              if (httpProxyBuilder.httpConnect) {
                skipToEnd = true
                httpConnectBuilder = httpProxyBuilder
                loopToSkipToEnd()
              } else {
                ctx.fireChannelRead(httpProxyBuilder.toProxyDumb(buf.resetReaderIndex()))
                ctx.pipeline().remove(this)
              }

            case Left(exception) =>
              // TODO
              exception.printStackTrace()
              ReferenceCountUtil.release(msg)
              ctx.close()
          }

        case Suspension =>

        case _ =>
          ReferenceCountUtil.release(msg)
          ctx.close()
      }
    } else {
      loopToSkipToEnd()
    }
  }

  @throws[Exception]
  private def toHttpProxyRequest(requestLine: String): Either[Throwable, HttpProxyBuilder] = {
    requestLineRegex.unapplySeq(requestLine)
      .toRight(new RuntimeException)
      .flatMap { m =>
        Try {
          val httpConnect = HttpMethod.valueOf(m.head) == HttpMethod.CONNECT
          val hostName = if (httpConnect)
            IpAddressUtil.toHostNameWithPort(m(1))
          else {
            val hostName = IpAddressUtil.toHostNameWithPortOption(m(1))
            if (hostName.getPort == null)
              new HostName(new InetSocketAddress(hostName.getHost, 80))
            else
              hostName
          }
          val httpVersion = HttpVersion.valueOf(m(2))

          HttpProxyBuilder(httpConnect, hostName, httpVersion)
        }.toEither
      }
  }
}

object HttpProxyFirstRequestHandler {

  // we don't support lenient parsing here
  // see https://tools.ietf.org/html/rfc7230#section-3.5
  private val requestLineRegex: Regex = "(.+) (?:http://)*(.+?)(?:/.*)* (.+)".r

  private final case class HttpProxyBuilder(httpConnect: Boolean, hostName: HostName, httpVersion: HttpVersion) {
    def toConnect: HttpProxy = {
      HttpConnect(hostName, httpVersion)
    }

    def toProxyDumb(buf: ByteBuf): HttpProxy = {
      HttpProxyDumb(hostName, httpVersion, buf)
    }
  }

}
