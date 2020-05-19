package cl.monsoon.star.client

import cl.monsoon.star.TimeoutUtil
import cl.monsoon.star.client.config.Proxy
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion._
import io.netty.handler.codec.socksx.v5._

@Sharable
final class ClientHandler(proxy: Proxy, devMode: Boolean) extends SimpleChannelInboundHandler[SocksMessage] {

  override def channelRead0(ctx: ChannelHandlerContext, socksRequest: SocksMessage): Unit = {
    socksRequest.version match {
      case SOCKS5 =>
        socksRequest match {
          case _: Socks5InitialRequest =>
            ctx.pipeline().remove(classOf[Socks5InitialRequestDecoder])
            TimeoutUtil.insertDecoder(ctx.pipeline(), new Socks5CommandRequestDecoder)
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))

          case socks5CmdRequest: Socks5CommandRequest =>
            ctx.pipeline().remove(classOf[Socks5CommandRequestDecoder])
            // TODO
            if (socks5CmdRequest.`type` == Socks5CommandType.CONNECT) {
              // TODO
              val serverInfo = proxy.server(proxy.default)
              val clientConnectHandler = ClientConnectHandler(proxy.default, serverInfo, devMode)
              ctx.pipeline().addLast(clientConnectHandler)
              ctx.fireChannelRead(socksRequest)
              ctx.pipeline().remove(this)
            } else ctx.close

          case _ => ctx.close
        }

      case _ =>
        ctx.close

    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
