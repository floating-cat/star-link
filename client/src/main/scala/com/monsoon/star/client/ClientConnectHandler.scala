package com.monsoon.star.client

import com.monsoon.star._
import com.monsoon.star.client.config.{ServerInfo, StringTag}
import com.monsoon.star.client.protocol.{ClientHelloEncoder, ClientHelloWsResponseHandler}
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.{DefaultSocks5CommandResponse, Socks5CommandRequest, Socks5CommandStatus}
import io.netty.util.concurrent.{Future, PromiseCombiner}

import scala.collection.concurrent.TrieMap

@Sharable
final class ClientConnectHandler private(stringTag: StringTag, serverInfo: ServerInfo, devMode: Boolean)
  extends SimpleChannelInboundHandler[SocksMessage] {

  override def channelRead0(ctx: ChannelHandlerContext, message: SocksMessage): Unit = {
    message match {
      case request: Socks5CommandRequest =>
        val promise = ctx.executor.newPromise[Channel]
        promise.addListener((future: Future[Channel]) => {
          if (future.isSuccess) {
            val outChannel = future.getNow
            val outPipe = outChannel.pipeline()

            outPipe.addLast(SslUtil.handler(outChannel, devMode),
              ClientHelloEncoder(stringTag, serverInfo))
            val requestFuture = outChannel.writeAndFlush(request)

            val responseFuture = ctx.channel.writeAndFlush(
              new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                request.dstAddrType, request.dstAddr, request.dstPort))

            outPipe.addLast(new ClientHelloWsResponseHandler(
              new RelayHandler(ctx.channel, RelayTag.ClientSender),
              TimeoutUtil.removeTimeoutHandlers(ctx.pipeline())))
            ctx.pipeline
              .addLast(new RelayHandler(outChannel, RelayTag.ClientReceiver))
              .remove(this)

            val combiner = new PromiseCombiner(ctx.executor())
            combiner.addAll(requestFuture, responseFuture)
            combiner.finish(ctx.newPromise().addListener((combinedFuture: ChannelFuture) => {
              if (!combinedFuture.isSuccess) {
                ChannelUtil.closeOnFlush(outChannel)
                ChannelUtil.closeOnFlush(ctx.channel)
              }
            }))
          } else {
            sendFailureResponse(ctx, request)
          }
        })

        val inboundChannel = ctx.channel
        new Bootstrap()
          .group(inboundChannel.eventLoop)
          .channel(Engine.Default.socketChannelClass)
          .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, TimeoutUtil.ConnectTimeoutMillis)
          .handler(new DirectClientHandler(promise))
          .connect(serverInfo.hostname.asInetSocketAddress())
          .addListener((future: ChannelFuture) => {
            if (!future.isSuccess) {
              sendFailureResponse(ctx, request)
            }
          })

      case _ => ctx.close
    }
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
