package cl.monsoon.star.client

import cl.monsoon.star.{ExceptionHandler, TimeoutUtil}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer}

import scala.util.chaining._

@Sharable
final class ClientInitializer(clientHandler: ClientHandler) extends ChannelInitializer[Channel] {

  private val httpOrSocks5InboundHandler = new HttpOrSocks5InboundHandler(clientHandler)

  override def initChannel(ch: Channel): Unit = {
    ch.pipeline()
      .pipe(TimeoutUtil.addTimeoutHandlers)
      .addLast(httpOrSocks5InboundHandler)
      .pipe(ExceptionHandler.add)
  }
}
