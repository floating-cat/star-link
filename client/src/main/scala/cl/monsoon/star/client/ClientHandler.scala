package cl.monsoon.star.client

import cl.monsoon.star.client.config.Proxy
import cl.monsoon.star.client.protocol.CommandRequest.HttpProxy
import cl.monsoon.star.client.rule._
import cl.monsoon.star.{BaseChannelInboundHandlerAdapter, TimeoutUtil}
import grizzled.slf4j.Logger
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler}
import io.netty.handler.codec.socksx.v5.{Socks5CommandRequest, _}

@Sharable
final class ClientHandler(proxy: Proxy, router: Router, devMode: Boolean) extends BaseChannelInboundHandlerAdapter {

  private val logger = Logger[this.type]

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
        } else {
          ctx.close
          logger.info(s"SOCKS5 command (${commandRequest.`type`}) refused")
        }

      case httpProxy: HttpProxy =>
        ctx.pipeline().addLast(decide(httpProxy.hostName.getHost))
        ctx.fireChannelRead(Left(httpProxy))
        ctx.pipeline().remove(this)

      case s =>
        ctx.close
        logger.warn(s"Illegal state (${s.getClass.getName}) when reading")
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
