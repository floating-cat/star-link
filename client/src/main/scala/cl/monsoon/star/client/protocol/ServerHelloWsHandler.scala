package cl.monsoon.star.client.protocol

import cl.monsoon.star.{BaseChannelInboundHandlerAdapter, End, HeaderParser, Suspension}
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext

final class ServerHelloWsHandler(EndAction: => Unit) extends BaseChannelInboundHandlerAdapter {

  private val headerParser = new HeaderParser()

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val buf = msg.asInstanceOf[ByteBuf]

    @scala.annotation.tailrec
    def loop(): Unit = {
      val result = headerParser.parse(buf)
      result match {
        case End =>
          EndAction
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
