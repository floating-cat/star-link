package cl.monsoon.star

import grizzled.slf4j.Logger
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}

import scala.util.chaining._

abstract class BaseSimpleChannelInboundHandler[I] extends SimpleChannelInboundHandler[I] {

  private val logger = Logger[this.type]()

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val (local, remote) = ctx.channel().pipe(c => (c.localAddress(), c.remoteAddress()))
    ctx.close()
    logger.error(s"Uncaught exception (Local and remote addresses: $local, $remote", cause)
  }
}
