package cl.monsoon.star

import io.netty.buffer.Unpooled
import io.netty.channel.{Channel, ChannelFutureListener}

import scala.language.implicitConversions

object ChannelUtil {

  def closeOnFlush(ch: Channel): Unit =
    if (ch.isActive)
      ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
        .addListener(ChannelFutureListener.CLOSE)
}
