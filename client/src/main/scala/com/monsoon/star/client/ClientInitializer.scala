package com.monsoon.star.client

import com.monsoon.star.TimeoutUtil
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.logging.{LogLevel, LoggingHandler}

import scala.util.chaining._

@Sharable
final class ClientInitializer(clientHandler: ClientHandler) extends ChannelInitializer[Channel] {

  override def initChannel(ch: Channel): Unit = {
    ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG))
      .pipe(TimeoutUtil.addTimeoutHandlers)
      .addLast(new SocksPortUnificationServerHandler,
        clientHandler)
  }
}
