package cl.monsoon.star.client

import grizzled.slf4j.Logger

import scala.sys.process._

object SystemProxy {

  private val logger = Logger[this.type]

  private val processLogger = ProcessLogger(_ => (), e => logger.error(s"Failed to set system proxy: $e"))

  def enable(listenIp: String, listenPort: Int): Unit = {
    run(
      s"""
         |gsettings set org.gnome.system.proxy mode 'manual' &&
         |gsettings set org.gnome.system.proxy.socks host '$listenIp' &&
         |gsettings set org.gnome.system.proxy.socks port $listenPort &&
         |gsettings set org.gnome.system.proxy.https host '$listenIp' &&
         |gsettings set org.gnome.system.proxy.https port $listenPort &&
         |gsettings set org.gnome.system.proxy.http host '$listenIp' &&
         |gsettings set org.gnome.system.proxy.http port $listenPort &&
         |gsettings set org.gnome.system.proxy.ftp host '$listenIp' &&
         |gsettings set org.gnome.system.proxy.ftp port $listenPort ;
         |
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "ProxyType" 1 &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "socksProxy" "socks://$listenIp $listenPort" &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "httpsProxy" "http://$listenIp $listenPort" &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "httpProxy" "http://$listenIp $listenPort" &&
         |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "ftpProxy" "http://$listenIp $listenPort"
         |""".stripMargin.replace('\n', ' '))
  }

  def addDisablingHook(): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        disable()
      }
    })
  }

  private def disable(): Unit = {
    run(
      """
        |gsettings set org.gnome.system.proxy mode "none" ;
        |kwriteconfig5 --file kioslaverc --group "Proxy Settings" --key "ProxyType" 0
        |""".stripMargin.replace('\n', ' '))
  }

  private def run(linuxCommands: => String): Unit = {
    val osName = System.getProperty("os.name").toLowerCase
    if (osName.contains("linux") && linuxCommands.nonEmpty) {
      Process(Seq("bash", "-c", linuxCommands)) ! processLogger
    }
  }
}
