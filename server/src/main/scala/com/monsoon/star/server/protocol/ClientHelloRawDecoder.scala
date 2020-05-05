package com.monsoon.star.server.protocol

import java.util

import com.monsoon.star.config.Password
import com.monsoon.star.server.protocol.ClientHelloInfoDecoder.decodeCommand
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.socksx.v5._
import io.netty.handler.codec.{ByteToMessageDecoder, ReplayingDecoder}

final class ClientHelloDecoder(password: Password) extends ByteToMessageDecoder {

  private val passwordBytesLength: Int = password.value.length
  private val pw: ByteBuf = Unpooled.wrappedBuffer(password.value)

  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes >= passwordBytesLength) {
      val passwordByteBuf = in.readBytes(passwordBytesLength)
      if (passwordByteBuf == pw) {
        passwordByteBuf.release()
        ctx.pipeline.addAfter(ctx.name, null, new ClientHelloInfoDecoder)
        ctx.pipeline.remove(this)
      } else {
        ctx.close
      }
    }
  }
}

final class ClientHelloInfoDecoder extends ReplayingDecoder[Void] {

  @throws[Exception]
  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    out.add(decodeCommand(in))
    ctx.pipeline.remove(this)
  }
}

object ClientHelloInfoDecoder {

  @throws[Exception]
  def decodeCommand(in: ByteBuf): DefaultSocks5CommandRequest = {
    val `type` = Socks5CommandType.valueOf(in.readByte)
    val dstAddrType = Socks5AddressType.valueOf(in.readByte)
    val dstAddr = Socks5AddressDecoder.DEFAULT.decodeAddress(dstAddrType, in)
    val dstPort = in.readUnsignedShort

    new DefaultSocks5CommandRequest(`type`, dstAddrType, dstAddr, dstPort)
  }
}
