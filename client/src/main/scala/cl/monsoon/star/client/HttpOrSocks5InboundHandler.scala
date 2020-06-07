package cl.monsoon.star.client

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler

@Sharable
final class HttpOrSocks5InboundHandler(clientHandler: ClientHandler) extends ChannelInboundHandlerAdapter {

  private val socks5ProtocolFirstByte: Byte = 5
  private val httpConnectProtocolFirstByte: Byte = 'C'.toByte

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val buf = msg.asInstanceOf[ByteBuf]
    val ctxPipe = ctx.pipeline()

    if (buf.readableBytes() >= 1) {
      val firstByte = buf.getByte(0)

      firstByte match {
        case `socks5ProtocolFirstByte` =>
          // assume SOCKS5
          ctxPipe.addLast(new SocksPortUnificationServerHandler)

        case `httpConnectProtocolFirstByte` =>
          // assume HTTP CONNECT
          ctxPipe.addLast(new HttpServerCodec())

        case _ =>
          buf.release()
          ctx.close()
          return
      }
    }

    ctxPipe.addLast(clientHandler)
    ctx.fireChannelRead(msg)
    ctxPipe.remove(this)
  }
}
