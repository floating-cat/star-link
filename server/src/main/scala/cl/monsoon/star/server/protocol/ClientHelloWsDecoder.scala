package cl.monsoon.star.server.protocol

import java.nio.charset.StandardCharsets
import java.util
import java.util.Base64

import cl.monsoon.star._
import cl.monsoon.star.config.Password
import cl.monsoon.star.server.protocol.ClientHelloWsDecoder.{clientHelloInfoRegex, passwordRegex, wsResponse}
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
    // a micro optimization might be done here
    val result = headerParser.parse(in)
    result match {
      case Value(v) if first =>
        first = false
        val pass = passwordRegex.findFirstMatchIn(v)
          .filter(m => Base64.getDecoder.decode(m.group(1)).sameElements(password.value))
        if (pass.isEmpty) ctx.close()

      case Value(clientHelloInfoRegex(v)) =>
        val buf = Unpooled.wrappedBuffer(Base64.getDecoder.decode(v))
        clientHelloInfo = Some(ClientHelloInfoDecoder.decodeCommand(buf))

      case Value(_) | Suspension =>

      case End if !first && clientHelloInfo.nonEmpty =>
        wsResponse.retain()
        ctx.writeAndFlush(wsResponse.duplicate(), ctx.voidPromise())
        out.add(clientHelloInfo.get)

        ctx.pipeline().remove(this)
        TimeoutUtil.removeTimeoutHandlers(ctx.pipeline())
      case _ =>
        ctx.close()
    }
  }
}

object ClientHelloWsDecoder {
  private val passwordRegex: Regex = "GET /([^ ]*) ".r
  private val clientHelloInfoRegex: Regex = "X-A:[ ]*([^ ]*)[ ]*".r

  // may increase the response length to avoid the side-channel leaks
  private val wsResponse: ByteBuf =
    Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(("HTTP/1.1 101 Switching Protocols\r\n" +
      "Upgrade:websocket\r\n" +
      "Connection:Upgrade\r\n\r\n")
      .getBytes(StandardCharsets.US_ASCII)))
}
