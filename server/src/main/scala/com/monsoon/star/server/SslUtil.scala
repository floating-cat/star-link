package com.monsoon.star.server

import io.netty.channel.Channel
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContextBuilder, SslHandler, SslProvider}

object SslUtil {

  def handler(ch: Channel): SslHandler = {
    val ssc = new SelfSignedCertificate
    val sslEngine = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
      .sslProvider(SslProvider.OPENSSL_REFCNT)
      .protocols("TLSv1.3", "TLSv1.2")
      .build()
      .newEngine(ch.alloc())
    new SslHandler(sslEngine)
  }
}
