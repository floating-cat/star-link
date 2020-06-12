package cl.monsoon.star

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator

object LogUtil {

  def setLevel(level: Level): Unit = {
    Configurator.setLevel("cl.monsoon.star", level)
  }
}
