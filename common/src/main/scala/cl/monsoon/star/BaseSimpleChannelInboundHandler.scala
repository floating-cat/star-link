package cl.monsoon.star

import grizzled.slf4j.Logger
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}

abstract class BaseSimpleChannelInboundHandler[I] extends SimpleChannelInboundHandler[I] {

  private val logger = Logger[this.type]()

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    ctx.close()
    logger.error("Uncaught exception", cause)
  }
}
