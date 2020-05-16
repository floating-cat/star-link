package com.monsoon.star

import java.net.SocketAddress

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandler
import io.netty.handler.logging.{LogLevel, LoggingHandler}

object BootstrapUtil {

  def server(socketAddress: SocketAddress, childHandler: ChannelHandler): Unit = {
    // TODO
    val group = Engine.Default.eventLoopGroup(1)
    try {
      new ServerBootstrap()
        .group(group)
        .channel(Engine.Default.serverSocketChannelClass)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(childHandler)
        .bind(socketAddress).sync()
        .channel()
        .closeFuture().sync()
    } finally {
      group.shutdownGracefully()
    }
  }
}
