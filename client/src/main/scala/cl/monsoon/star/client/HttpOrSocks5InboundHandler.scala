package cl.monsoon.star.client

import cl.monsoon.star.BaseChannelInboundHandlerAdapter
import cl.monsoon.star.client.protocol.HttpProxyFirstRequestHandler
import grizzled.slf4j.Logger
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler

@Sharable
final class HttpOrSocks5InboundHandler(clientHandler: ClientHandler) extends BaseChannelInboundHandlerAdapter {

  private val logger = Logger[this.type]

  private val socks4ProtocolFirstByte: Byte = 4
  private val socks5ProtocolFirstByte: Byte = 5

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val buf = msg.asInstanceOf[ByteBuf]
    val ctxPipe = ctx.pipeline()

    if (buf.readableBytes() >= 1) {
      val firstByte = buf.getByte(0)

      firstByte match {
        case `socks5ProtocolFirstByte` =>
          // assume SOCKS5
          ctxPipe.addLast(new SocksPortUnificationServerHandler)

        case `socks4ProtocolFirstByte` =>
          // assume SOCKS4
          // we don't support SOCKS4
          buf.release()
          ctx.close()
          logger.info("SOCKS4 request refused")
          return

        case _ =>
          // assume HTTP Proxy
          ctxPipe.addLast(new HttpProxyFirstRequestHandler())
      }
    }

    ctxPipe.addLast(clientHandler)
    ctx.fireChannelRead(msg)
    ctxPipe.remove(this)
  }
}
