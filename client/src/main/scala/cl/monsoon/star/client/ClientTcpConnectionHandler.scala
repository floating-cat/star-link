package cl.monsoon.star.client

import java.net.InetSocketAddress

import cl.monsoon.star._
import cl.monsoon.star.client.config.{ProxyTag, ServerInfo}
import cl.monsoon.star.client.protocol.CommandRequest.{HttpConnect, HttpProxyDumb, HttpProxyOrSocks5}
import cl.monsoon.star.client.protocol.{ClientHelloEncoder, ServerHelloWsHandler}
import grizzled.slf4j.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.socksx.v5.{DefaultSocks5CommandResponse, Socks5CommandStatus, Socks5ServerEncoder}
import io.netty.handler.ssl.SslContext
import io.netty.util.concurrent.Future

import scala.collection.concurrent.TrieMap
import scala.util.chaining._

@Sharable
private sealed trait ClientTcpConnectionHandler extends BaseChannelInboundHandlerAdapter {

  protected val logger: Logger = Logger[this.type]()

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val httpProxyOrSocks = msg.asInstanceOf[HttpProxyOrSocks5]
    val promise = ctx.executor.newPromise[Channel]
    val inChannel = ctx.channel
    promise.addListener { future: Future[Channel] =>
      if (future.isSuccess) {
        onConnected(httpProxyOrSocks, inChannel, future.getNow)
      } else {
        sendFailureResponse(httpProxyOrSocks, inChannel)
      }
    }

    new Bootstrap()
      .group(inChannel.eventLoop)
      .channel(NettyEngine.Default.socketChannelClass)
      .option[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, TimeoutUtil.ConnectTimeoutMillis)
      .handler(new DirectClientHandler(promise))
      .connect(serverSocketAddress(httpProxyOrSocks))
      .addListener { future: ChannelFuture =>
        if (!future.isSuccess) {
          sendFailureResponse(httpProxyOrSocks, inChannel)
        }
      }
  }

  protected def serverSocketAddress(httpProxyOrSocks: HttpProxyOrSocks5): InetSocketAddress

  protected def onConnected(httpProxyOrSocks: HttpProxyOrSocks5,
                            inChannel: Channel, outChannel: Channel): Unit

  final protected def sendSuccessResponseAndStartRelay(httpProxyOrSocks: HttpProxyOrSocks5,
                                                       inChannel: Channel, outChannel: Channel): Unit = {
    val (response, responseOutputChannel) = httpProxyOrSocks match {
      case Right(socks5) =>
        (new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
          socks5.dstAddrType, socks5.dstAddr, socks5.dstPort),
          inChannel)

      case Left(httpConnect: HttpConnect) =>
        inChannel.pipeline().addLast(new HttpResponseEncoder)
        (new DefaultHttpResponse(httpConnect.httpVersion, HttpResponseStatus.OK),
          inChannel)

      case Left(httpProxyDumb: HttpProxyDumb) =>
        (httpProxyDumb.requestBuf, outChannel)
    }

    writeResponseAndRelay(httpProxyOrSocks, response, responseOutputChannel, inChannel, outChannel)
  }

  private def writeResponseAndRelay(httpProxyOrSocks: HttpProxyOrSocks5, response: Any,
                                    responseOutputChannel: Channel,
                                    inChannel: Channel, outChannel: Channel) = {
    responseOutputChannel.writeAndFlush(response)
      .addListener { responseFuture: ChannelFuture =>
        if (responseFuture.isSuccess) {
          relayAndRemoveUnneededHandlers(httpProxyOrSocks, inChannel, outChannel)
        } else {
          ChannelUtil.closeOnFlush(inChannel)
          ChannelUtil.closeOnFlush(outChannel)
          logger.warn(s"Unable to send response for ${httpProxyOrSocks.merge}")
        }
      }
  }

  private def relayAndRemoveUnneededHandlers(httpProxyOrSocks: HttpProxyOrSocks5,
                                             inChannel: Channel, outChannel: Channel) = {
    val inPipe = inChannel.pipeline
    inPipe
      .addLast(new RelayHandler(outChannel, RelayTag.ClientReceiver))
      .remove(this)
      .pipe(TimeoutUtil.removeTimeoutHandlers)

    outChannel.pipeline()
      .addLast(new RelayHandler(inChannel, RelayTag.ClientSender))

    httpProxyOrSocks match {
      case Right(_) =>
        inPipe.remove(classOf[Socks5ServerEncoder])

      case Left(_: HttpConnect) =>
        inPipe.remove(classOf[HttpResponseEncoder])

      case Left(_: HttpProxyDumb) =>
        inChannel.config().setAutoRead(true)
    }
  }

  final protected def sendFailureResponse(httpProxyOrSocks: HttpProxyOrSocks5, inChannel: Channel): Unit = {
    val response = httpProxyOrSocks.fold(
      httpProxy => new DefaultHttpResponse(httpProxy.httpVersion, HttpResponseStatus.INTERNAL_SERVER_ERROR),
      socks5 => new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, socks5.dstAddrType))

    inChannel.writeAndFlush(response, inChannel.voidPromise())
    ChannelUtil.closeOnFlush(inChannel)
    logger.warn(s"Send failure response for ${httpProxyOrSocks.merge}")
  }
}

@Sharable
private final class ClientTcpConnectionProxyHandler(stringTag: ProxyTag,
                                                    sslContext: SslContext,
                                                    serverInfo: ServerInfo) extends ClientTcpConnectionHandler {

  override def serverSocketAddress(httpProxyOrSocks: HttpProxyOrSocks5): InetSocketAddress =
    serverInfo.hostname.asInetSocketAddress()

  override def onConnected(httpProxyOrSocks: HttpProxyOrSocks5, inChannel: Channel, outChannel: Channel): Unit = {
    logger.debug(s"Proxy request for ${httpProxyOrSocks.merge}")

    outChannel.pipeline().addLast(
      sslContext.newHandler(outChannel.alloc(), serverInfo.hostname.getHost, serverInfo.hostname.getPort),
      ClientHelloEncoder(stringTag, serverInfo))

    outChannel.writeAndFlush(httpProxyOrSocks)
      .addListener { requestFuture: ChannelFuture =>
        if (requestFuture.isSuccess) {
          outChannel.pipeline()
            .addLast(new ServerHelloWsHandler(
              sendSuccessResponseAndStartRelay(httpProxyOrSocks, inChannel, outChannel)))
        } else {
          sendFailureResponse(httpProxyOrSocks, inChannel)
          ChannelUtil.closeOnFlush(outChannel)
        }
      }
  }
}

@Sharable
private object ClientTcpConnectionDirectHandler extends ClientTcpConnectionHandler {

  override def serverSocketAddress(httpProxyOrSocks: HttpProxyOrSocks5): InetSocketAddress =
    httpProxyOrSocks.fold(
      _.hostName.asInetSocketAddress(),
      socks5 => new InetSocketAddress(socks5.dstAddr, socks5.dstPort))

  override def onConnected(httpProxyOrSocks: HttpProxyOrSocks5, inChannel: Channel, outChannel: Channel): Unit = {
    sendSuccessResponseAndStartRelay(httpProxyOrSocks, inChannel, outChannel)
    logger.debug(s"Direct request: ${httpProxyOrSocks.merge}")
  }
}

@Sharable
private object ClientConnectionRejectHandler extends BaseChannelInboundHandlerAdapter {

  protected val logger: Logger = Logger[this.type]()

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val httpProxyOrSocks = msg.asInstanceOf[HttpProxyOrSocks5]
    val inContext = ctx
    val response = httpProxyOrSocks.fold(
      httpProxy => new DefaultHttpResponse(httpProxy.httpVersion, HttpResponseStatus.FORBIDDEN),
      socks5 => new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, socks5.dstAddrType))

    inContext.channel.writeAndFlush(response, inContext.voidPromise())
    ChannelUtil.closeOnFlush(inContext.channel)
    logger.debug(s"Reject request: ${httpProxyOrSocks.merge}")
  }
}

object ClientTcpConnectionHandler {

  private val proxyHandlerInstancePool = TrieMap[ProxyTag, ClientTcpConnectionHandler]()
  val direct: ChannelInboundHandler = ClientTcpConnectionDirectHandler
  val reject: ChannelInboundHandler = ClientConnectionRejectHandler

  def proxy(stringTag: ProxyTag, serverInfo: ServerInfo, devMode: Boolean): ChannelInboundHandler = {
    proxyHandlerInstancePool.getOrElseUpdate(stringTag,
      new ClientTcpConnectionProxyHandler(stringTag, SslUtil.context(devMode), serverInfo))
  }
}
