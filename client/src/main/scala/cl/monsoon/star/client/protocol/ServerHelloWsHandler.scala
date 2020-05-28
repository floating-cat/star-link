package cl.monsoon.star.client.protocol

import cl.monsoon.star.{End, HeaderParser, Suspension}
import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

final class ServerHelloWsHandler(firstEndAction: => Unit) extends ChannelInboundHandlerAdapter {

  private val headerParser = new HeaderParser()

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val buf = msg.asInstanceOf[ByteBuf]

    @scala.annotation.tailrec
    def loop(): Unit = {
      // a micro optimization might be done here
      val result = headerParser.parse(buf)
      result match {
        case End =>
          firstEndAction
          ctx.fireChannelRead(buf)
          ctx.pipeline().remove(this)
        case Suspension =>
        case _ =>
          loop()
      }
    }

    loop()
  }
}
