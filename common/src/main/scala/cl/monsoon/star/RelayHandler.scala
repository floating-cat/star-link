package cl.monsoon.star

import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.ReferenceCountUtil

import scala.annotation.unused

final class RelayHandler(relayChannel: Channel, @unused tag: RelayTag.Value) extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit =
    if (relayChannel.isActive)
      relayChannel.write(msg, relayChannel.voidPromise())
    else
      ReferenceCountUtil.release(msg)

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    relayChannel.flush()

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit =
    relayChannel.config().setAutoRead(ctx.channel().isWritable)

  override def channelInactive(ctx: ChannelHandlerContext): Unit =
    ChannelUtil.closeOnFlush(relayChannel)
}

object RelayTag extends Enumeration {
  val ClientSender, ClientReceiver, ServerSender, ServerReceiver = Value
}
