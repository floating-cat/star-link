package com.monsoon.star.client.protocol

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.monsoon.star.client.config.{ServerInfo, StringTag}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.codec.socksx.v5.Socks5AddressType.{DOMAIN, IPv4, IPv6}
import io.netty.handler.codec.socksx.v5.{Socks5AddressEncoder, Socks5CommandRequest}

import scala.collection.concurrent.TrieMap

sealed abstract class ClientHelloEncoder extends MessageToByteEncoder[Socks5CommandRequest]

object ClientHelloEncoder {

  private val pool = TrieMap[StringTag, ClientHelloEncoder]()

  def apply(tag: StringTag, serverInfo: ServerInfo): ClientHelloEncoder =
    pool.getOrElseUpdate(tag, new ClientHelloWSEncoder(serverInfo))

  @Sharable
  private final class ClientHelloRawEncoder(serverInfo: ServerInfo) extends ClientHelloEncoder {

    override def encode(ctx: ChannelHandlerContext, msg: Socks5CommandRequest, out: ByteBuf): Unit = {
      out.writeBytes(serverInfo.password.value)
      writeCommand(msg, out)
      ctx.pipeline.remove(this)
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

    override def encode(ctx: ChannelHandlerContext, msg: Socks5CommandRequest, out: ByteBuf): Unit = {
      val buf = ctx.alloc().heapBuffer(length(msg))
      writeCommand(msg, buf)
      out.writeBytes(requestBytesPrefix)
      out.writeBytes(Base64.getEncoder.encode(buf.nioBuffer()))
      buf.release()
      out.writeBytes(requestBytesSuffix)
      ctx.pipeline.remove(this)
    }

    private def length(msg: Socks5CommandRequest): Int = {
      // bytes length:
      // 1: message type + 1: address type +
      // 4: IPv4 | 16: IPv6 | (1: domain name length + 1–255: domain name) +
      // 1: port
      1 + 1 + (msg.dstAddrType match {
        case IPv4 => 4
        case IPv6 => 16
        case DOMAIN => 1 + msg.dstAddr().length
      }) + 2
    }
  }

  private def writeCommand(msg: Socks5CommandRequest, out: ByteBuf): Unit = {
    out.writeByte(msg.`type`.byteValue)
    val dstAddrType = msg.dstAddrType
    out.writeByte(dstAddrType.byteValue)
    Socks5AddressEncoder.DEFAULT.encodeAddress(dstAddrType, msg.dstAddr, out)
    out.writeShort(msg.dstPort)
  }
}
