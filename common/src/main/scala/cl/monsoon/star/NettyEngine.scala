package cl.monsoon.star

import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueueEventLoopGroup, KQueueServerSocketChannel, KQueueSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.socket.{ServerSocketChannel, SocketChannel}
import io.netty.channel.{EventLoopGroup, epoll, kqueue}

sealed trait NettyEngine {
  def eventLoopGroup(nThreads: Int = 0): EventLoopGroup

  val serverSocketChannelClass: Class[_ <: ServerSocketChannel]

  val socketChannelClass: Class[_ <: SocketChannel]
}

object NettyEngine {

  val Default: NettyEngine = if (epoll.Epoll.isAvailable)
    Epoll
  else if (kqueue.KQueue.isAvailable)
    KQueue
  else
    Nio

  private object Nio extends NettyEngine {
    override def eventLoopGroup(nThreads: Int = 0) = new NioEventLoopGroup(nThreads)

    override val serverSocketChannelClass = classOf[NioServerSocketChannel]
    override val socketChannelClass = classOf[NioSocketChannel]
  }

  private object Epoll extends NettyEngine {
    override def eventLoopGroup(nThreads: Int = 0) = new EpollEventLoopGroup(nThreads)

    override val serverSocketChannelClass = classOf[EpollServerSocketChannel]
    override val socketChannelClass = classOf[EpollSocketChannel]
  }

  private object KQueue extends NettyEngine {
    override def eventLoopGroup(nThreads: Int = 0): EventLoopGroup = new KQueueEventLoopGroup(nThreads)

    override val serverSocketChannelClass = classOf[KQueueServerSocketChannel]
    override val socketChannelClass = classOf[KQueueSocketChannel]
  }

}
