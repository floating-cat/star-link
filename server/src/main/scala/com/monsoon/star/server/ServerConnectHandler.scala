package com.monsoon.star.server

import com.monsoon.star._
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.util.concurrent.Future

final class ServerConnectHandler extends ChannelInboundHandlerAdapter {

  private var receivedClientHello = false
  private var buffer: ByteBuf = Unpooled.EMPTY_BUFFER

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    if (!receivedClientHello) {
      receivedClientHello = true

      val request = msg.asInstanceOf[Socks5CommandRequest]
      val promise = ctx.executor.newPromise[Channel]
      promise.addListener((future: Future[Channel]) => {
        val outChannel = future.getNow
        if (future.isSuccess) {
          outChannel.pipeline().addLast(new RelayHandler(ctx.channel, RelayTag.ServerSender))
          ctx.pipeline().addLast(new RelayHandler(outChannel, RelayTag.ServerReceiver))
          ctx.pipeline().remove(this)
          outChannel.writeAndFlush(buffer)
        } else {
          ChannelUtil.closeOnFlush(ctx.channel)
          buffer.release()
        }
      })

      val inboundChannel = ctx.channel
      new Bootstrap().group(inboundChannel.eventLoop)
        .channel(Engine.Default.socketChannelClass)
        .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
        .handler(new DirectClientHandler(promise))
        .connect(request.dstAddr, request.dstPort).addListener((future: ChannelFuture) => {
        if (!future.isSuccess) {
          ChannelUtil.closeOnFlush(ctx.channel)

          buffer.release()
        }
      })
    } else {
      buffer = Unpooled.wrappedBuffer(buffer, msg.asInstanceOf[ByteBuf])
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
