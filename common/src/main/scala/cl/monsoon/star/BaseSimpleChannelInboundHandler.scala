package cl.monsoon.star

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}

abstract class BaseSimpleChannelInboundHandler[I] extends SimpleChannelInboundHandler[I] {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
