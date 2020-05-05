package com.monsoon.star.config

import org.apache.commons.codec.binary.Hex
import pureconfig.error.{CannotConvert, FailureReason}

import scala.util.{Success, Try}

final case class Port private(value: Int) extends AnyVal

object Port {

  def apply(port: Int): Either[String, Port] =
    Either.cond(port >= 0 && port < 65536,
      new Port(port), "The port number must be between from 0 and 65535")
}

case class Password private(value: Array[Byte]) extends AnyVal

object Password {

  private val passwordLength = 32

  def apply(s: String): Either[String, Password] = {
    val x = Option.when(s.length == passwordLength)(Try(Hex.decodeHex(s)))
    x match {
      case Some(Success(bytes)) => Right(Password(bytes))
      case _ => Left("The password length should be 16 hex characters in length")
    }
  }
}
