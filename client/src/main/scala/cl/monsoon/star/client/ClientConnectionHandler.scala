package cl.monsoon.star.client

import java.net.InetSocketAddress

import cl.monsoon.star._
import cl.monsoon.star.client.config.{ProxyTag, ServerInfo}
import cl.monsoon.star.client.protocol.CommandRequest.HttpOrSocks5
import cl.monsoon.star.client.protocol.{ClientHelloEncoder, ServerHelloWsHandler}
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http.{DefaultHttpResponse, HttpResponseStatus, HttpServerCodec}
import io.netty.handler.codec.socksx.v5.{DefaultSocks5CommandResponse, Socks5CommandStatus, Socks5ServerEncoder}
import io.netty.util.concurrent.Future

import scala.collection.concurrent.TrieMap
import scala.util.chaining._

@Sharable
private sealed trait ClientConnectionHandler extends SimpleChannelInboundHandler[HttpOrSocks5] {

  final override def channelRead0(inContext: ChannelHandlerContext, httpOrSocks5: HttpOrSocks5): Unit = {
    val promise = inContext.executor.newPromise[Channel]
    promise.addListener((future: Future[Channel]) => {
      if (future.isSuccess) {
        onConnected(httpOrSocks5, inContext, future.getNow)
      } else {
        sendFailureResponse(httpOrSocks5, inContext)
      }
    })

    val inboundChannel = inContext.channel
    new Bootstrap()
      .group(inboundChannel.eventLoop)
      .channel(NettyEngine.Default.socketChannelClass)
      .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, TimeoutUtil.ConnectTimeoutMillis)
      .handler(new DirectClientHandler(promise))
      .connect(serverSocketAddress(httpOrSocks5))
      .addListener((future: ChannelFuture) => {
        if (!future.isSuccess) {
          sendFailureResponse(httpOrSocks5, inContext)
        }
      })
  }

  def serverSocketAddress(httpOrSocks5: HttpOrSocks5): InetSocketAddress

  def onConnected(httpOrSocks5: HttpOrSocks5,
                  inContext: ChannelHandlerContext, outChannel: Channel): Unit

  final protected def sendSuccessResponseAndStartRelay(httpOrSocks5: HttpOrSocks5,
                                                       inContext: ChannelHandlerContext, outChannel: Channel): Unit = {
    val response = httpOrSocks5.fold(
      http => new DefaultHttpResponse(http.version, HttpResponseStatus.OK),
      socks5 => new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
        socks5.dstAddrType, socks5.dstAddr, socks5.dstPort))

    inContext.channel.writeAndFlush(response)
      .addListener((responseFuture: ChannelFuture) => {
        if (responseFuture.isSuccess) {
          relayAndRemoveUnneededHandlers(httpOrSocks5, inContext, outChannel)
        } else {
          ChannelUtil.closeOnFlush(inContext.channel)
          ChannelUtil.closeOnFlush(outChannel)
        }
      })
  }

  private def relayAndRemoveUnneededHandlers(httpOrSocks5: HttpOrSocks5,
                                             inContext: ChannelHandlerContext, outChannel: Channel) = {
    inContext.pipeline
      .pipe(ExceptionHandler.addBeforeIt(_, new RelayHandler(outChannel, RelayTag.ClientReceiver)))
      .remove(this)
      .pipe(TimeoutUtil.removeTimeoutHandlers)
      .tap {
        ctxPipe =>
          if (httpOrSocks5.isLeft) {
            ctxPipe.remove(classOf[HttpServerCodec])
          } else {
            ctxPipe.remove(classOf[Socks5ServerEncoder])
          }
      }
    outChannel.pipeline()
      .addLast(new RelayHandler(inContext.channel, RelayTag.ClientSender))
      .pipe(ExceptionHandler.add)
  }

  final protected def sendFailureResponse(httpOrSocks5: HttpOrSocks5, inContext: ChannelHandlerContext): Unit = {
    val response = httpOrSocks5.fold(
      http => new DefaultHttpResponse(http.version, HttpResponseStatus.INTERNAL_SERVER_ERROR),
      socks5 => new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, socks5.dstAddrType))

    inContext.channel().writeAndFlush(response)
    ChannelUtil.closeOnFlush(inContext.channel)
  }
}

@Sharable
private final class ClientConnectionProxyHandler(stringTag: ProxyTag, serverInfo: ServerInfo,
                                                 devMode: Boolean) extends ClientConnectionHandler {

  override def serverSocketAddress(httpOrSocks5: HttpOrSocks5): InetSocketAddress =
    serverInfo.hostname.asInetSocketAddress()

  override def onConnected(httpOrSocks5: HttpOrSocks5, inContext: ChannelHandlerContext, outChannel: Channel): Unit = {
    outChannel.pipeline().addLast(
      SslUtil.handler(outChannel, serverInfo, devMode),
      ClientHelloEncoder(stringTag, serverInfo))

    outChannel.writeAndFlush(httpOrSocks5)
      .addListener((requestFuture: ChannelFuture) => {
        if (requestFuture.isSuccess) {
          outChannel.pipeline().addLast(new ServerHelloWsHandler(
            sendSuccessResponseAndStartRelay(httpOrSocks5, inContext, outChannel)))
        } else {
          sendFailureResponse(httpOrSocks5, inContext)
          ChannelUtil.closeOnFlush(outChannel)
        }
      })
  }
}

@Sharable
private object ClientConnectionDirectHandler extends ClientConnectionHandler {

  override def serverSocketAddress(httpOrSocks5: HttpOrSocks5): InetSocketAddress =
    httpOrSocks5.fold(
      _.hostName.asInetSocketAddress(),
      socks5 => new InetSocketAddress(socks5.dstAddr, socks5.dstPort))

  override def onConnected(httpOrSocks5: HttpOrSocks5, inContext: ChannelHandlerContext, outChannel: Channel): Unit = {
    sendSuccessResponseAndStartRelay(httpOrSocks5, inContext, outChannel)
  }
}

@Sharable
private object ClientConnectionRejectHandler extends SimpleChannelInboundHandler[HttpOrSocks5] {

  override def channelRead0(inContext: ChannelHandlerContext, httpOrSocks5: HttpOrSocks5): Unit = {
    val response = httpOrSocks5.fold(
      http => new DefaultHttpResponse(http.version, HttpResponseStatus.FORBIDDEN),
      socks5 => new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, socks5.dstAddrType))

    inContext.channel.writeAndFlush(response)
    ChannelUtil.closeOnFlush(inContext.channel)
  }
}

object ClientConnectionHandler {

  private val proxyHandlerInstancePool = TrieMap[ProxyTag, ClientConnectionHandler]()
  val direct: ChannelInboundHandler = ClientConnectionDirectHandler
  val reject: ChannelInboundHandler = ClientConnectionRejectHandler

  def proxy(stringTag: ProxyTag, serverInfo: ServerInfo, devMode: Boolean): ChannelInboundHandler =
    proxyHandlerInstancePool.getOrElseUpdate(stringTag, new ClientConnectionProxyHandler(stringTag, serverInfo, devMode))
}
