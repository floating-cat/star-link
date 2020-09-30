package cl.monsoon.star.client

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder, SslProvider}

import scala.util.chaining._

object SslUtil {

  def context(trustInsecure: Boolean): SslContext = {
    SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL)
      .pipe { builder =>
        if (!trustInsecure) {
          builder
        } else {
          builder.trustManager(InsecureTrustManagerFactory.INSTANCE)
        }
      }.build()
  }
}
