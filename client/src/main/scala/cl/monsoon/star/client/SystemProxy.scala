package cl.monsoon.star.client

import java.net.InetSocketAddress

import grizzled.slf4j.Logger

import scala.sys.process._
import scala.util.Try

object SystemProxy {

  private val logger = Logger[this.type]()

  private val processLogger = ProcessLogger(_ => (), e => logger.error(s"Failed to set system proxy, $e"))

  def enable(socketAddress: InetSocketAddress): Unit = {
    val host = if (socketAddress.getHostString == "0.0.0.0") "127.0.0.1" else socketAddress.getHostString
    val port = socketAddress.getPort

    val linuxCommandsMultiline =
      s"""
         |gsettings set org.gnome.system.proxy mode 'manual' &&
         |gsettings set org.gnome.system.proxy.socks host '$host' &&
         |gsettings set org.gnome.system.proxy.socks port $port &&
         |gsettings set org.gnome.system.proxy.https host '$host' &&
         |gsettings set org.gnome.system.proxy.https port $port &&
         |gsettings set org.gnome.system.proxy.http host '$host' &&
         |gsettings set org.gnome.system.proxy.http port $port &&
         |gsettings set org.gnome.system.proxy.ftp host '$host' &&
         |gsettings set org.gnome.system.proxy.ftp port $port ;
         |
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "ProxyType" 1 &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "socksProxy" "socks://$host $port" &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "httpsProxy" "http://$host $port" &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "httpProxy" "http://$host $port" &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "ftpProxy" "http://$host $port"
         |"""

    run(runForLinux(linuxCommandsMultiline),
      runForWindows(proxy = true, host, port))
  }

  def addDisablingHook(): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        disable()
      }
    })
  }

  private def disable(): Unit = {
    val linuxCommandsMultiline =
      """
        |gsettings set org.gnome.system.proxy mode "none" ;
        |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "ProxyType" 0
        |"""

    run(runForLinux(linuxCommandsMultiline),
      runForWindows(proxy = false, null, 0))
  }

  private def run(linuxAction: => Unit, windowsAction: => Unit): Unit = {
    val osName = System.getProperty("os.name").toLowerCase
    if (osName.contains("linux")) {
      linuxAction
    } else if (osName.contains("windows")) {
      windowsAction
    }
  }

  private def runForLinux(linuxCommandsMultiline: String): Unit = {
    val commands = linuxCommandsMultiline.stripMargin.replace('\n', ' ')
    Process(Seq("bash", "-c", commands)) ! processLogger
  }

  private def runForWindows(proxy: Boolean, host: String, listenPort: Int): Unit = {
    Try(WindowsProxy.setNet(proxy, host, listenPort))
      .failed.foreach(logger.error("Failed to set system proxy", _))
  }
}
