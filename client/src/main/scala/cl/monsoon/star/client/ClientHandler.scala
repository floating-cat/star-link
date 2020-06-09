package cl.monsoon.star.client

import java.net.InetSocketAddress

import cl.monsoon.star.client.config.Proxy
import cl.monsoon.star.client.protocol.CommandRequest.HttpProxyRequest
import cl.monsoon.star.client.rule._
import cl.monsoon.star.config.IpAddressUtil
import cl.monsoon.star.{BaseChannelInboundHandlerAdapter, TimeoutUtil}
import inet.ipaddr.HostName
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler}
import io.netty.handler.codec.http.{HttpMethod, HttpRequest}
import io.netty.handler.codec.socksx.v5.{Socks5CommandRequest, _}

import scala.util.{Failure, Success, Try}

@Sharable
final class ClientHandler(proxy: Proxy, router: Router, devMode: Boolean) extends BaseChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case _: Socks5InitialRequest =>
        ctx.pipeline().remove(classOf[Socks5InitialRequestDecoder])
        TimeoutUtil.addAfterIt(ctx.pipeline(), new Socks5CommandRequestDecoder)
        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH), ctx.voidPromise())

      case commandRequest: Socks5CommandRequest =>
        val ctxPipe = ctx.pipeline()
        ctxPipe.remove(classOf[Socks5CommandRequestDecoder])
        // TODO
        if (commandRequest.`type` == Socks5CommandType.CONNECT) {
          ctxPipe.addLast(decide(commandRequest.dstAddr()))
          ctx.fireChannelRead(Right(commandRequest))
          ctxPipe.remove(this)
        } else ctx.close

      case httpRequest: HttpRequest =>
        if (httpRequest.method() == HttpMethod.CONNECT) {
          val hostNameTry = Try(IpAddressUtil.toHostNameWithPort(httpRequest.uri()))
          handleHttpsRequest(httpRequest, hostNameTry, ctx, isHttpConnect = true)
        } else {
          val hostNameTry = Try(IpAddressUtil.toHostNameWithPortOption(httpRequest.headers().get("host")))
            .map(hostName =>
              if (hostName.getPort == null)
                new HostName(new InetSocketAddress(hostName.getHost, 80))
              else
                hostName)
          handleHttpsRequest(httpRequest, hostNameTry, ctx, isHttpConnect = false)
        }

      case _ => ctx.close
    }
  }

  private def decide(address: String): ChannelInboundHandler =
    router.decide(address) match {
      case ProxyRouteResult(tag) => ClientConnectionHandler.proxy(tag, proxy.server(tag), devMode)
      case DefaultProxyRouteResult => ClientConnectionHandler.proxy(proxy.default, proxy.server(proxy.default), devMode)
      case DirectRouteResult => ClientConnectionHandler.direct
      case RejectRouteResult => ClientConnectionHandler.reject
    }

  private def handleHttpsRequest(httpRequest: HttpRequest, hostNameTry: Try[HostName],
                                 ctx: ChannelHandlerContext, isHttpConnect: Boolean): Unit = {
    hostNameTry match {
      case Success(hostname) =>
        ctx.pipeline().addLast(decide(hostname.getHost))
        ctx.fireChannelRead(Left(HttpProxyRequest(httpRequest, hostname, isHttpConnect)))
        ctx.pipeline().remove(this)

      case Failure(exception) =>
        // TODO
        exception.printStackTrace()
        ctx.close
    }
  }
}
