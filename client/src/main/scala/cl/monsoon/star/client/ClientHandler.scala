package cl.monsoon.star.client

import cl.monsoon.star.TimeoutUtil
import cl.monsoon.star.client.config.Proxy
import cl.monsoon.star.client.protocol.CommandRequest.HttpsRequest
import cl.monsoon.star.client.rule._
import cl.monsoon.star.config.IpAddressUtil
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.socksx.v5.{Socks5CommandRequest, _}

import scala.util.{Success, Try}

@Sharable
final class ClientHandler(proxy: Proxy, router: Router, devMode: Boolean) extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val ctxPipe = ctx.pipeline()
    msg match {
      case _: Socks5InitialRequest =>
        ctx.pipeline().remove(classOf[Socks5InitialRequestDecoder])
        TimeoutUtil.addAfterIt(ctx.pipeline(), new Socks5CommandRequestDecoder)
        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))

      case commandRequest: Socks5CommandRequest =>
        ctxPipe.remove(classOf[Socks5CommandRequestDecoder])
        // TODO
        if (commandRequest.`type` == Socks5CommandType.CONNECT) {
          ctxPipe.addLast(decide(commandRequest.dstAddr()))
          ctx.fireChannelRead(Right(commandRequest))
          ctxPipe.remove(this)
        } else ctx.close

      case httpRequest: HttpRequest =>
        Try(IpAddressUtil.toHostNameWithPort(httpRequest.uri())) match {
          case Success(hostname) =>
            ctxPipe.addLast(decide(hostname.getHost))
            ctx.fireChannelRead(Left(HttpsRequest(hostname, httpRequest.protocolVersion())))
            ctxPipe.remove(this)

          case _ => ctx.close
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
}
