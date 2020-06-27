package cl.monsoon.star

import grizzled.slf4j.Logger
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPipeline}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.timeout.{IdleStateEvent, IdleStateHandler}

object TimeoutUtil {

  val ConnectTimeoutMillis: Int = 10000
  private val idleStateEventHandler: ChannelDuplexHandler = IdleStateEventHandler

  def addTimeoutHandlers(pipe: ChannelPipeline): ChannelPipeline =
    pipe.addLast(new IdleStateHandler(10, 10, 0))
      .addLast("IdleStateEventHandler", idleStateEventHandler)

  def removeTimeoutHandlers(pipe: ChannelPipeline): ChannelPipeline = {
    pipe.remove(idleStateEventHandler)
      .remove(classOf[IdleStateHandler])
    pipe
  }

  // https://github.com/netty/netty/issues/6842
  def addAfterIt(pipe: ChannelPipeline, decoder: ByteToMessageDecoder): ChannelPipeline =
    pipe.addAfter("IdleStateEventHandler", null, decoder)
}

@Sharable
private object IdleStateEventHandler extends ChannelDuplexHandler {

  private val logger = Logger[this.type]()

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    evt match {
      case event: IdleStateEvent =>
        ctx.close()
        logger.warn(s"Timeout: $event")
      case _ =>
    }
  }
}
