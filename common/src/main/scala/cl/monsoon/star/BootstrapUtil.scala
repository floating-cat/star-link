package cl.monsoon.star

import java.net.SocketAddress

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{ChannelFuture, ChannelHandler}

object BootstrapUtil {

  def server(socketAddress: SocketAddress, childHandler: ChannelHandler, bindAction: => Unit = ()): Unit = {
    val group = NettyEngine.Default.eventLoopGroup(1)
    try {
      new ServerBootstrap()
        .group(group)
        .channel(NettyEngine.Default.serverSocketChannelClass)
        .childHandler(childHandler)
        .bind(socketAddress)
        .addListener { future: ChannelFuture =>
          if (future.isSuccess) {
            bindAction
          }
        }.sync()
        .channel()
        .closeFuture().sync()
    } finally {
      group.shutdownGracefully()
    }
  }
}
