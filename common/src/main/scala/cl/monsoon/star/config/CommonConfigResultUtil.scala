package cl.monsoon.star.config

import pureconfig.error.{CannotConvert, FailureReason}

import scala.util.Try

object CommonConfigResultUtil {

  def toPort(port: Int): Either[FailureReason, Port] = {
    Port(port)
      .left.map(CannotConvert(port.toString, "port", _))
  }

  def toPort(port: String): Either[FailureReason, Port] = {
    Try(port.toInt)
      .toEither
      .left.map(_ => WrongPortType(port))
      .flatMap(toPort)
  }

  def toPassword(password: String): Either[FailureReason, Password] = {
    Password(password)
      .left.map(err => CannotConvert(password, "password", err))
  }

  final case class WrongPortType(port: String) extends FailureReason {
    def description = s"Unable to covert '$port' to a port number."
  }

}
