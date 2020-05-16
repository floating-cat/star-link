package com.monsoon.star

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPipeline}
import io.netty.handler.timeout.{IdleStateEvent, IdleStateHandler}

object TimeoutUtil {

  val ConnectTimeoutMillis: Int = 10000
  private val IdleStateEventHandler: IdleStateEventHandler = new IdleStateEventHandler

  def addTimeoutHandlers(pipe: ChannelPipeline): ChannelPipeline =
    pipe.addLast(new IdleStateHandler(10, 10, 0),
      IdleStateEventHandler)

  def removeTimeoutHandlers(pipe: ChannelPipeline): ChannelPipeline = {
    pipe.remove(IdleStateEventHandler)
      .remove(classOf[IdleStateHandler])
    pipe
  }
}

@Sharable
private class IdleStateEventHandler extends ChannelDuplexHandler {

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    evt match {
      case _: IdleStateEvent =>
        ctx.close
      case _ =>
    }
  }
}
