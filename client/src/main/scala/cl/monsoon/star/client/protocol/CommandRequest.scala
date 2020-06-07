package cl.monsoon.star.client.protocol

import inet.ipaddr.HostName
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

object CommandRequest {

  final case class HttpProxyRequest(httpRequest: HttpRequest, hostName: HostName, isHttpConnect: Boolean)

  type HttpOrSocks5 = Either[HttpProxyRequest, Socks5CommandRequest]
}
