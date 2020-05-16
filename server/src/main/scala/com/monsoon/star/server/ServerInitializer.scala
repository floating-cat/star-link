package com.monsoon.star.server

import com.monsoon.star.TimeoutUtil
import com.monsoon.star.server.config.ServerConfig
import com.monsoon.star.server.protocol.ClientHelloWsDecoder
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.handler.ssl.SslMasterKeyHandler

import scala.util.chaining._

final class ServerInitializer(config: ServerConfig) extends ChannelInitializer[Channel] {

  override def initChannel(ch: Channel): Unit = {
    ch.pipeline.addLast(new LoggingHandler(LogLevel.DEBUG))
      .pipe(TimeoutUtil.addTimeoutHandlers)
      .pipe {
        pipeline =>
          if (config.devMode) {
            System.setProperty(SslMasterKeyHandler.SYSTEM_PROP_KEY, true.toString)
            pipeline.addLast(SslUtil.handler(ch), SslMasterKeyHandler.newWireSharkSslMasterKeyHandler())
          } else pipeline
      }
      .addLast(new ClientHelloWsDecoder(config.password),
        new ServerConnectHandler)
  }
}
