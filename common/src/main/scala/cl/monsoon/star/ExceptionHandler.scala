package cl.monsoon.star

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter, ChannelPipeline}

@Sharable
object ExceptionHandler extends ChannelInboundHandlerAdapter {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }

  def add(pipe: ChannelPipeline): ChannelPipeline =
    pipe.addLast("ExceptionHandler", this)

  def addBeforeIt(pipe: ChannelPipeline, channelHandler: ChannelHandler): ChannelPipeline =
    pipe.addBefore("ExceptionHandler", null, channelHandler)
}
