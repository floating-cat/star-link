package com.monsoon.star.client

import com.monsoon.star._
import com.monsoon.star.client.config.{ServerInfo, StringTag}
import com.monsoon.star.client.protocol.{ClientHelloEncoder, ClientHelloWsResponseHandler}
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.{DefaultSocks5CommandResponse, Socks5CommandRequest, Socks5CommandStatus}
import io.netty.util.concurrent.Future

import scala.collection.concurrent.TrieMap
import scala.util.chaining._

@Sharable
final class ClientConnectHandler private(stringTag: StringTag, serverInfo: ServerInfo, devMode: Boolean)
  extends SimpleChannelInboundHandler[SocksMessage] {

  override def channelRead0(inContext: ChannelHandlerContext, message: SocksMessage): Unit = {
    message match {
      case commandRequest: Socks5CommandRequest =>
        val promise = inContext.executor.newPromise[Channel]
        promise.addListener((future: Future[Channel]) => {
          val outChannel = future.getNow
          outChannel.pipeline().addLast(
            SslUtil.handler(outChannel, devMode),
            ClientHelloEncoder(stringTag, serverInfo))

          outChannel.writeAndFlush(commandRequest)
            .addListener((requestFuture: ChannelFuture) => {
              if (requestFuture.isSuccess) {
                sendSuccessResponseAndStartRelay(inContext, outChannel, commandRequest)
              } else {
                sendFailureResponse(inContext, commandRequest)
                ChannelUtil.closeOnFlush(outChannel)
              }
            })
        })

        val inboundChannel = inContext.channel
        new Bootstrap()
          .group(inboundChannel.eventLoop)
          .channel(NettyEngine.Default.socketChannelClass)
          .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, TimeoutUtil.ConnectTimeoutMillis)
          .handler(new DirectClientHandler(promise))
          .connect(serverInfo.hostname.asInetSocketAddress())
          .addListener((future: ChannelFuture) => {
            if (!future.isSuccess) {
              sendFailureResponse(inContext, commandRequest)
            }
          })

      case _ => inContext.close
    }
  }

  private def sendSuccessResponseAndStartRelay(inContext: ChannelHandlerContext, outChannel: Channel,
                                               commandRequest: Socks5CommandRequest) = {
    outChannel.pipeline().addLast(new ClientHelloWsResponseHandler({
      val response = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
        commandRequest.dstAddrType, commandRequest.dstAddr, commandRequest.dstPort)

      inContext.channel.writeAndFlush(response)
        .addListener((responseFuture: ChannelFuture) => {
          if (responseFuture.isSuccess) {
            relayAndRemoveUnneededHandlers(inContext, outChannel)
          } else {
            ChannelUtil.closeOnFlush(inContext.channel)
            ChannelUtil.closeOnFlush(outChannel)
          }
        })
    }))
  }

  private def relayAndRemoveUnneededHandlers(inContext: ChannelHandlerContext, outChannel: Channel) = {
    inContext.pipeline
      .addLast(new RelayHandler(outChannel, RelayTag.ClientReceiver))
      .remove(this)
      .pipe(TimeoutUtil.removeTimeoutHandlers)
    outChannel.pipeline()
      .addLast(new RelayHandler(inContext.channel, RelayTag.ClientSender))
  }

  private def sendFailureResponse(ctx: ChannelHandlerContext, request: Socks5CommandRequest): Unit = {
    ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
      Socks5CommandStatus.FAILURE, request.dstAddrType))
    ChannelUtil.closeOnFlush(ctx.channel)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}

object ClientConnectHandler {

  private val pool = TrieMap[StringTag, ClientConnectHandler]()

  def apply(stringTag: StringTag, serverInfo: ServerInfo, devMode: Boolean): ClientConnectHandler =
    pool.getOrElseUpdate(stringTag, new ClientConnectHandler(stringTag, serverInfo, devMode))
}
