package cl.monsoon.star.client

import io.netty.channel.ChannelFuture

object ChannelFutureUtil {

  def foreachOrCloseItWhenFailed(future: ChannelFuture, input: => Unit): Unit = {
    if (future.isSuccess) {
      input
    } else {
      future.channel()
    }
  }
}
