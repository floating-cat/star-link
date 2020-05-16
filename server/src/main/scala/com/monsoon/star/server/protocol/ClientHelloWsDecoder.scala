package com.monsoon.star.server.protocol

import java.nio.charset.StandardCharsets
import java.util
import java.util.Base64

import com.monsoon.star._
import com.monsoon.star.config.Password
import com.monsoon.star.server.protocol.ClientHelloWsDecoder.{ClientHelloInfoRegex, PasswordRegex, WsResponse}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.socksx.v5._

import scala.util.matching.Regex

final class ClientHelloWsDecoder(password: Password) extends ByteToMessageDecoder {

  private val headerParser = new HeaderParser()
  private var first: Boolean = true
  private var clientHelloInfo: Option[DefaultSocks5CommandRequest] = None

  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    // TODO: A micro optimization might be done here
    val result = headerParser.parse(in)
    result match {
      case Value(v) if first =>
        first = false
        val pass = PasswordRegex.findFirstMatchIn(v)
          .filter(m => Base64.getDecoder.decode(m.group(1)).sameElements(password.value))
        if (pass.isEmpty) ctx.close()

      case Value(ClientHelloInfoRegex(v)) =>
        val buf = Unpooled.wrappedBuffer(Base64.getDecoder.decode(v))
        clientHelloInfo = Some(ClientHelloInfoDecoder.decodeCommand(buf))

      case Value(_) | Suspension =>

      case End if !first && clientHelloInfo.nonEmpty =>
        WsResponse.retain()
        ctx.writeAndFlush(WsResponse.duplicate())
        out.add(clientHelloInfo.get)

        ctx.pipeline().remove(this)
        TimeoutUtil.removeTimeoutHandlers(ctx.pipeline())
      case _ =>
        ctx.close()
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}

object ClientHelloWsDecoder {
  private val PasswordRegex: Regex = "GET /([^ ]*) ".r
  private val ClientHelloInfoRegex: Regex = "X-A:[ ]*([^ ]*)[ ]*".r

  // may increase the response length to avoid the side-channel leaks
  private val WsResponse: ByteBuf =
    Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(("HTTP/1.1 101 Switching Protocols\r\n" +
      "Upgrade:websocket\r\n" +
      "Connection:Upgrade\r\n\r\n")
      .getBytes(StandardCharsets.US_ASCII)))
}
