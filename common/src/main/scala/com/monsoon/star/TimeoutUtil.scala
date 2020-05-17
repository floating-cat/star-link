package com.monsoon.star

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPipeline}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.timeout.{IdleStateEvent, IdleStateHandler}

object TimeoutUtil {

  val ConnectTimeoutMillis: Int = 10000
  private val IdleStateEventHandler: IdleStateEventHandler = new IdleStateEventHandler

  def addTimeoutHandlers(pipe: ChannelPipeline): ChannelPipeline =
    pipe.addLast(new IdleStateHandler(10, 10, 0))
      .addLast("IdleStateEventHandler", IdleStateEventHandler)

  def removeTimeoutHandlers(pipe: ChannelPipeline): ChannelPipeline = {
    pipe.remove(IdleStateEventHandler)
      .remove(classOf[IdleStateHandler])
    pipe
  }

  // https://github.com/netty/netty/issues/6842
  def insertDecoder(pipe: ChannelPipeline, decoder: ByteToMessageDecoder): ChannelPipeline =
    pipe.addAfter("IdleStateEventHandler", null, decoder)
}

@Sharable
private class IdleStateEventHandler extends ChannelDuplexHandler {

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    evt match {
      case _: IdleStateEvent =>
        ctx.pipeline().channel().close()
      case _ =>
    }
  }
}
