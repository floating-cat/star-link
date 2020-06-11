package cl.monsoon.star.client.protocol

import inet.ipaddr.HostName
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

object CommandRequest {

  // TODO Scala 3 trait
  sealed abstract class HttpProxy {
    def hostName: HostName

    def httpVersion: HttpVersion
  }

  final case class HttpConnect(hostName: HostName, httpVersion: HttpVersion) extends HttpProxy

  final case class HttpProxyDumb(hostName: HostName, httpVersion: HttpVersion, requestBuf: ByteBuf) extends HttpProxy

  type HttpProxyOrSocks5 = Either[HttpProxy, Socks5CommandRequest]
}
