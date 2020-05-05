package com.monsoon.star.client

import io.netty.channel.Channel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContextBuilder, SslHandler}

object SslUtil {

  def handler(ch: Channel): SslHandler = {
    val sslContext = SslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .build()
    val sslEngine = sslContext.newEngine(ch.alloc())
    new SslHandler(sslEngine)
  }
}
