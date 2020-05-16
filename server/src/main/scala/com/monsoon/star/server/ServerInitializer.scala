package com.monsoon.star.server

import com.monsoon.star.server.config.ServerConfig
import com.monsoon.star.server.protocol.ClientHelloWsDecoder
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.handler.ssl.SslMasterKeyHandler

final class ServerInitializer(config: ServerConfig) extends ChannelInitializer[Channel] {

  override def initChannel(ch: Channel): Unit = {
    val pipe = ch.pipeline
    pipe.addLast(new LoggingHandler(LogLevel.DEBUG))

    if (config.devMode) {
      System.setProperty(SslMasterKeyHandler.SYSTEM_PROP_KEY, true.toString)
      pipe.addLast(SslUtil.handler(ch), SslMasterKeyHandler.newWireSharkSslMasterKeyHandler())
    }

    pipe.addLast(new ClientHelloWsDecoder(config.password), new ServerConnectHandler)
  }
}
