package cl.monsoon.star

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

class BaseChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
