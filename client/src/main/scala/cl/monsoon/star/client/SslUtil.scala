package cl.monsoon.star.client

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder, SslProvider}

import scala.util.chaining._

object SslUtil {

  def context(trustInsecure: Boolean): SslContext = {
    SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL)
      // TODO
      .protocols("TLSv1.2", "TLSv1.3")
      .pipe { builder =>
        if (!trustInsecure) {
          builder
        } else {
          builder.trustManager(InsecureTrustManagerFactory.INSTANCE)
        }
      }.build()
  }
}
