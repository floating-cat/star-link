package cl.monsoon.star.client

import cl.monsoon.star.TimeoutUtil
import cl.monsoon.star.client.config.{Proxy, Rule}
import cl.monsoon.star.client.rule._
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler, SimpleChannelInboundHandler}
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion._
import io.netty.handler.codec.socksx.v5.{Socks5CommandRequest, _}

@Sharable
final class ClientHandler(proxy: Proxy, rule: Rule, devMode: Boolean) extends SimpleChannelInboundHandler[SocksMessage] {

  private val router: Router = new Router(rule)

  override def channelRead0(ctx: ChannelHandlerContext, socksRequest: SocksMessage): Unit = {
    socksRequest.version match {
      case SOCKS5 =>
        socksRequest match {
          case _: Socks5InitialRequest =>
            ctx.pipeline().remove(classOf[Socks5InitialRequestDecoder])
            TimeoutUtil.insertDecoder(ctx.pipeline(), new Socks5CommandRequestDecoder)
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))

          case commandRequest: Socks5CommandRequest =>
            ctx.pipeline().remove(classOf[Socks5CommandRequestDecoder])
            // TODO
            if (commandRequest.`type` == Socks5CommandType.CONNECT) {
              ctx.pipeline().addLast(decide(commandRequest))
              ctx.fireChannelRead(socksRequest)
              ctx.pipeline().remove(this)
            } else ctx.close

          case _ => ctx.close
        }

      case _ =>
        ctx.close

    }
  }

  private def decide(commandRequest: Socks5CommandRequest): ChannelInboundHandler =
    router.decide(commandRequest) match {
      case ProxyRouteResult(tag) => ClientConnectionHandler.proxy(tag, proxy.server(tag), devMode)
      case DefaultProxyRouteResult => ClientConnectionHandler.proxy(proxy.default, proxy.server(proxy.default), devMode)
      case DirectRouteResult => ClientConnectionHandler.direct
      case RejectRouteResult => ClientConnectionHandler.reject
    }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
