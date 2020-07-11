package cl.monsoon.star.server

import cl.monsoon.star.TimeoutUtil
import cl.monsoon.star.server.config.ServerConfig
import cl.monsoon.star.server.protocol.ClientHelloWsDecoder
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer}

import scala.util.chaining._

@Sharable
final class ServerInitializer(config: ServerConfig) extends ChannelInitializer[Channel] {

  private val sslContext = SslUtil.context

  override def initChannel(ch: Channel): Unit = {
    ch.pipeline()
      .pipe(TimeoutUtil.addTimeoutHandlers)
      .pipe {
        pipeline =>
          if (config.testMode) {
            pipeline.addLast(sslContext.newHandler(ch.alloc()))
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
