package cl.monsoon.star.client.protocol

import inet.ipaddr.HostName
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

object CommandRequest {

  final case class HttpsRequest(hostName: HostName, version: HttpVersion)

  type HttpOrSocks5 = Either[HttpsRequest, Socks5CommandRequest]
}
