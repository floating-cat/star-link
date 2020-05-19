package cl.monsoon.star.client

import cl.monsoon.star.TimeoutUtil
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler

import scala.util.chaining._

@Sharable
final class ClientInitializer(clientHandler: ClientHandler) extends ChannelInitializer[Channel] {

  override def initChannel(ch: Channel): Unit = {
    ch.pipeline()
      .pipe(TimeoutUtil.addTimeoutHandlers)
      .addLast(new SocksPortUnificationServerHandler,
        clientHandler)
  }
}
