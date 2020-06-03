package cl.monsoon.star.client

import cl.monsoon.star.client.config.ServerInfo
import io.netty.channel.Channel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContextBuilder, SslHandler, SslProvider}

import scala.util.chaining._

object SslUtil {

  def handler(ch: Channel, serverInfo: ServerInfo, trustInsecure: Boolean): SslHandler = {
    val sslEngine = SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL_REFCNT)
      // TODO
      .protocols("TLSv1.2", "TLSv1.3")
      .pipe { builder =>
        if (!trustInsecure) {
          builder
        } else {
          builder.trustManager(InsecureTrustManagerFactory.INSTANCE)
        }
      }.build()
      .newEngine(ch.alloc(), serverInfo.hostname.getHost, serverInfo.hostname.getPort)

    new SslHandler(sslEngine)
  }
}
