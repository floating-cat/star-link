package cl.monsoon.star.server

import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContext, SslContextBuilder, SslProvider}

object SslUtil {

  def context: SslContext = {
    val ssc = new SelfSignedCertificate
    SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
      .sslProvider(SslProvider.OPENSSL)
      .protocols("TLSv1.2", "TLSv1.3")
      .build()
  }
}
