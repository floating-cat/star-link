package com.monsoon.star.server

import io.netty.channel.Channel
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContextBuilder, SslHandler}

object SslUtil {

  def handler(ch: Channel): SslHandler = {
    val ssc = new SelfSignedCertificate
    val sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build()
    val sslEngine = sslContext.newEngine(ch.alloc())
    new SslHandler(sslEngine)
  }
}
