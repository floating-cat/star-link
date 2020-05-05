package com.monsoon.star

import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.ReferenceCountUtil

final class RelayHandler(val relayChannel: Channel, val tag: RelayTag.Value) extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    if (relayChannel.isActive)
      relayChannel.write(msg)
    else
      ReferenceCountUtil.release(msg)
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    relayChannel.flush()

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    ChannelUtil.closeOnFlush(relayChannel)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    // TODO
    cause.printStackTrace()
    ctx.close
  }
}

object RelayTag extends Enumeration {
  val ClientSender, ClientReceiver, ServerSender, ServerReceiver = Value
}
