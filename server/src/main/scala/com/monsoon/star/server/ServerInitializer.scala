package com.monsoon.star.server

import com.monsoon.star.TimeoutUtil
import com.monsoon.star.server.config.ServerConfig
import com.monsoon.star.server.protocol.ClientHelloWsDecoder
import io.netty.channel.{Channel, ChannelInitializer}

import scala.util.chaining._

final class ServerInitializer(config: ServerConfig) extends ChannelInitializer[Channel] {

  override def initChannel(ch: Channel): Unit = {
    ch.pipeline()
      .pipe(TimeoutUtil.addTimeoutHandlers)
      .pipe {
        pipeline =>
          if (config.testMode) {
            pipeline.addLast(SslUtil.handler(ch))
            // TODO
            // SslMasterKeyHandler.newWireSharkSslMasterKeyHandler() doesn't support TLS 1.3
            // System.setProperty(SslMasterKeyHandler.SYSTEM_PROP_KEY, true.toString)
            // SslMasterKeyHandler.newWireSharkSslMasterKeyHandler()
          } else pipeline
      }
      .addLast(new ClientHelloWsDecoder(config.password),
        new ServerConnectHandler)
  }
}
