package com.monsoon.star.client

import io.netty.channel.Channel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContextBuilder, SslHandler, SslProvider}

import scala.util.chaining._

object SslUtil {

  def handler(ch: Channel, trustInsecure: Boolean): SslHandler = {
    val sslEngine = SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL_REFCNT)
      .pipe { builder =>
        if (!trustInsecure) {
          builder.protocols("TLSv1.3")
        } else {
          builder.protocols("TLSv1.3", "TLSv1.2")
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
        }
      }.build()
      .newEngine(ch.alloc())

    new SslHandler(sslEngine)
  }
}
