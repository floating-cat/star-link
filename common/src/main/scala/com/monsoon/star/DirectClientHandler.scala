package com.monsoon.star

import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.concurrent.Promise

final class DirectClientHandler(promise: Promise[Channel]) extends ChannelInboundHandlerAdapter {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    ctx.pipeline.remove(this)
    promise.setSuccess(ctx.channel)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    promise.setFailure(cause)
  }
}
