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

  override def channelRead(inContext: ChannelHandlerContext, msg: Any): Unit = {
    if (!receivedClientHello) {
      receivedClientHello = true

      val request = msg.asInstanceOf[Socks5CommandRequest]
      val promise = inContext.executor.newPromise[Channel]
      promise.addListener((future: Future[Channel]) => {
        val outChannel = future.getNow
        if (future.isSuccess) {
          inContext.pipeline()
            .addLast(new RelayHandler(outChannel, RelayTag.ServerReceiver))
            .remove(this)
          outChannel.pipeline()
            .addLast(new RelayHandler(inContext.channel, RelayTag.ServerSender))
            .writeAndFlush(buffer)
        } else {
          ChannelUtil.closeOnFlush(inContext.channel)
          buffer.release()
        }
      })

      val inboundChannel = inContext.channel
      new Bootstrap().group(inboundChannel.eventLoop)
        .channel(Engine.Default.socketChannelClass)
        .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, TimeoutUtil.ConnectTimeoutMillis)
        .handler(new DirectClientHandler(promise))
        .connect(request.dstAddr, request.dstPort).addListener((future: ChannelFuture) => {
        if (!future.isSuccess) {
          ChannelUtil.closeOnFlush(inContext.channel)
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
