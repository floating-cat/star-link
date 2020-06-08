package cl.monsoon.star

import io.netty.channel.{Channel, ChannelHandlerContext}
import io.netty.util.concurrent.Promise

final class DirectClientHandler(promise: Promise[Channel]) extends BaseChannelInboundHandlerAdapter {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    ctx.pipeline().remove(this)
    promise.setSuccess(ctx.channel)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    super.exceptionCaught(ctx, cause)
    promise.setFailure(cause)
  }
}
