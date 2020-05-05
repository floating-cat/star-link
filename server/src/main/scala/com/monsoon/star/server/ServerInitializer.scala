package com.monsoon.star.server

import com.monsoon.star.config.Password
import com.monsoon.star.server.protocol.ClientHelloWsDecoder
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.logging.{LogLevel, LoggingHandler}

final class ServerInitializer(password: Password) extends ChannelInitializer[Channel] {

  override def initChannel(ch: Channel): Unit = {

    ch.pipeline.addLast(new LoggingHandler(LogLevel.DEBUG),
      // SslUtil.handler(ch),
      new ClientHelloWsDecoder(password),
      new ServerConnectHandler)
  }
}
