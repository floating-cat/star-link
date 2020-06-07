package cl.monsoon.star.client.protocol

import java.nio.charset.StandardCharsets
import java.util.Base64

import cl.monsoon.star.client.config.{ProxyTag, ServerInfo}
import cl.monsoon.star.client.protocol.CommandRequest.HttpOrSocks5
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.codec.socksx.v5.Socks5AddressType.{DOMAIN, IPv4, IPv6}
import io.netty.handler.codec.socksx.v5._

import scala.annotation.unused
import scala.collection.concurrent.TrieMap

sealed abstract class ClientHelloEncoder extends MessageToByteEncoder[HttpOrSocks5]

object ClientHelloEncoder {

  private val pool = TrieMap[ProxyTag, ClientHelloEncoder]()

  def apply(tag: ProxyTag, serverInfo: ServerInfo): ClientHelloEncoder =
    pool.getOrElseUpdate(tag, new ClientHelloWSEncoder(serverInfo))

  @unused
  @Sharable
  private final class ClientHelloRawEncoder(serverInfo: ServerInfo) extends ClientHelloEncoder {

    override def encode(ctx: ChannelHandlerContext, msg: HttpOrSocks5, out: ByteBuf): Unit = {
      out.writeBytes(serverInfo.password.value)
      writeCommand(toSocks5CommandRequest(msg), out)
      ctx.pipeline().remove(this)
    }
  }

  @Sharable
  private final class ClientHelloWSEncoder(serverInfo: ServerInfo) extends ClientHelloEncoder {

    private val requestBytesPrefix: Array[Byte] =
      (s"GET /${Base64.getEncoder.encodeToString(serverInfo.password.value)} HTTP/1.1\r\n" +
        s"Host:${serverInfo.hostname.getHost}\r\n" +
        "Upgrade:websocket\r\n" +
        "Connection:Upgrade\r\n" +
        "X-A:").getBytes(StandardCharsets.US_ASCII)

    private val requestBytesSuffix =
      "\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    override def encode(ctx: ChannelHandlerContext, msg: HttpOrSocks5, out: ByteBuf): Unit = {
      val commandRequest = toSocks5CommandRequest(msg)
      val buf = ctx.alloc().heapBuffer(length(commandRequest))
      writeCommand(commandRequest, buf)
      out.writeBytes(requestBytesPrefix)
      out.writeBytes(Base64.getEncoder.encode(buf.nioBuffer()))
      buf.release()
      out.writeBytes(requestBytesSuffix)
      ctx.pipeline().remove(this)
    }

    private def length(msg: Socks5CommandRequest): Int = {
      // bytes length:
      // 1: message type + 1: address type +
      // 4: IPv4 | 16: IPv6 | (1: domain name length + 1–255: domain name) +
      // 2: port
      1 + 1 + (msg.dstAddrType match {
        case IPv4 => 4
        case IPv6 => 16
        case DOMAIN => 1 + msg.dstAddr().length
      }) + 2
    }
  }

  private def writeCommand(msg: Socks5CommandRequest, out: ByteBuf): Unit = {
    out.writeByte(msg.`type`.byteValue.toInt)
    val dstAddrType = msg.dstAddrType
    out.writeByte(dstAddrType.byteValue.toInt)
    Socks5AddressEncoder.DEFAULT.encodeAddress(dstAddrType, msg.dstAddr, out)
    out.writeShort(msg.dstPort)
  }

  private def toSocks5CommandRequest(httpOrSocks5: HttpOrSocks5): Socks5CommandRequest = {
    httpOrSocks5.fold(
      http => new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN,
        http.hostName.getHost, http.hostName.getPort),
      identity
    )
  }
}
