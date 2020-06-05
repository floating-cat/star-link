package cl.monsoon.star.client

import java.net.{InetSocketAddress, SocketAddress}

import cl.monsoon.star._
import cl.monsoon.star.client.config.{ProxyTag, ServerInfo}
import cl.monsoon.star.client.protocol.{ClientHelloEncoder, ServerHelloWsHandler}
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.{DefaultSocks5CommandResponse, Socks5CommandRequest, Socks5CommandStatus, Socks5ServerEncoder}
import io.netty.util.concurrent.Future

import scala.collection.concurrent.TrieMap
import scala.util.chaining._

@Sharable
private sealed trait ClientConnectionHandler extends SimpleChannelInboundHandler[SocksMessage] {

  override def channelRead0(inContext: ChannelHandlerContext, message: SocksMessage): Unit = {
    message match {
      case commandRequest: Socks5CommandRequest =>
        val promise = inContext.executor.newPromise[Channel]
        promise.addListener((future: Future[Channel]) => {
          if (future.isSuccess) {
            onConnected(commandRequest, inContext, future.getNow)
          } else {
            sendFailureResponse(inContext, commandRequest)
          }
        })

        val inboundChannel = inContext.channel
        new Bootstrap()
          .group(inboundChannel.eventLoop)
          .channel(NettyEngine.Default.socketChannelClass)
          .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, TimeoutUtil.ConnectTimeoutMillis)
          .handler(new DirectClientHandler(promise))
          .connect(serverSocketAddress(commandRequest))
          .addListener((future: ChannelFuture) => {
            if (!future.isSuccess) {
              sendFailureResponse(inContext, commandRequest)
            }
          })

      case _ => inContext.close
    }
  }

  def serverSocketAddress(commandRequest: Socks5CommandRequest): SocketAddress

  def onConnected(commandRequest: Socks5CommandRequest,
                  inContext: ChannelHandlerContext, outChannel: Channel): Unit

  final protected def sendSuccessResponseAndStartRelay(commandRequest: Socks5CommandRequest,
                                                       inContext: ChannelHandlerContext, outChannel: Channel): Unit = {
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
  }

  private def relayAndRemoveUnneededHandlers(inContext: ChannelHandlerContext, outChannel: Channel) = {
    inContext.pipeline
      .addLast(new RelayHandler(outChannel, RelayTag.ClientReceiver))
      .remove(this)
      .pipe(TimeoutUtil.removeTimeoutHandlers)
      .remove(classOf[Socks5ServerEncoder])
    outChannel.pipeline()
      .addLast(new RelayHandler(inContext.channel, RelayTag.ClientSender))
  }

  final protected def sendFailureResponse(inContext: ChannelHandlerContext, commandRequest: Socks5CommandRequest): Unit = {
    inContext.channel().writeAndFlush(new DefaultSocks5CommandResponse(
      Socks5CommandStatus.FAILURE, commandRequest.dstAddrType))
    ChannelUtil.closeOnFlush(inContext.channel)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}

@Sharable
private final class ClientConnectionProxyHandler(stringTag: ProxyTag, serverInfo: ServerInfo,
                                                 devMode: Boolean) extends ClientConnectionHandler {

  override def serverSocketAddress(commandRequest: Socks5CommandRequest): SocketAddress =
    serverInfo.hostname.asInetSocketAddress()

  override def onConnected(commandRequest: Socks5CommandRequest,
                           inContext: ChannelHandlerContext, outChannel: Channel): Unit = {
    outChannel.pipeline().addLast(
      SslUtil.handler(outChannel, serverInfo, devMode),
      ClientHelloEncoder(stringTag, serverInfo))

    outChannel.writeAndFlush(commandRequest)
      .addListener((requestFuture: ChannelFuture) => {
        if (requestFuture.isSuccess) {
          outChannel.pipeline().addLast(new ServerHelloWsHandler(
            sendSuccessResponseAndStartRelay(commandRequest, inContext, outChannel)))
        } else {
          sendFailureResponse(inContext, commandRequest)
          ChannelUtil.closeOnFlush(outChannel)
        }
      })
  }
}

@Sharable
private object ClientConnectionDirectHandler extends ClientConnectionHandler {

  override def serverSocketAddress(commandRequest: Socks5CommandRequest): SocketAddress =
    new InetSocketAddress(commandRequest.dstAddr, commandRequest.dstPort)

  override def onConnected(commandRequest: Socks5CommandRequest,
                           inContext: ChannelHandlerContext, outChannel: Channel): Unit = {
    sendSuccessResponseAndStartRelay(commandRequest, inContext, outChannel)
  }
}

@Sharable
private object ClientConnectionRejectHandler extends SimpleChannelInboundHandler[SocksMessage] {

  override def channelRead0(inContext: ChannelHandlerContext, message: SocksMessage): Unit = {
    message match {
      case commandRequest: Socks5CommandRequest =>
        inContext.channel().writeAndFlush(new DefaultSocks5CommandResponse(
          Socks5CommandStatus.FORBIDDEN, commandRequest.dstAddrType))
        ChannelUtil.closeOnFlush(inContext.channel)

      case _ => inContext.close
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}

object ClientConnectionHandler {

  private val proxyHandlerInstancePool = TrieMap[ProxyTag, ClientConnectionHandler]()
  val direct: ChannelInboundHandler = ClientConnectionDirectHandler
  val reject: ChannelInboundHandler = ClientConnectionRejectHandler

  def proxy(stringTag: ProxyTag, serverInfo: ServerInfo, devMode: Boolean): ChannelInboundHandler =
    proxyHandlerInstancePool.getOrElseUpdate(stringTag, new ClientConnectionProxyHandler(stringTag, serverInfo, devMode))
}
