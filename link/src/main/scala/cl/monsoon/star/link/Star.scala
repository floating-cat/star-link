package cl.monsoon.star.link

import java.nio.file.{Files, Path, Paths}

import cats.data.Validated
import cats.implicits._
import cl.monsoon.star.BuildInfo
import cl.monsoon.star.client.Client
import cl.monsoon.star.link.StarUtil.validateExist
import cl.monsoon.star.server.Server
import com.monovore.decline.Opts.flag
import com.monovore.decline._

import scala.util.chaining._

object Star extends CommandApp(
  name = "star",
  header = s"${BuildInfo.name} ${BuildInfo.version}",
  helpFlag = false,
  main = {

    // the default one doesn't have short name
    val helpOpt = flag("help", "Display this help text.", "h", Visibility.Partial).asHelp

    val clientOpt = Opts.flag("client", "Run as a client", "c")
      .as(CommandType.Client)
    // TODO: JDK 13
    val clientConfigOpt = Opts.argument[Path](metavar = "config file (default \"client.conf\")")
      .withDefault(Paths.get("client.conf"))
      .pipe(validateExist)

    val serverOpt = Opts.flag("server", "Run as a server", "s")
      .as(CommandType.Server)
    val serverConfigOpt = Opts.argument[Path](metavar = "config file (default \"server.conf\")")
      .withDefault(Paths.get("server.conf"))
      .pipe(validateExist)

    helpOpt.orElse((clientOpt, clientConfigOpt).tupled)
      .orElse((serverOpt, serverConfigOpt).tupled).map {
      case (CommandType.Client, path) =>
        Client.run(path)
      case (_, path) =>
        Server.run(path)
    }
  }
)

private object StarUtil {

  def validateExist(pathOpt: Opts[Path]): Opts[Path] = {
    pathOpt.mapValidated { path =>
      if (Files.exists(path)) Validated.valid(path)
      else Validated.invalidNel(s"The specified config file doesn't exist or is unreadable: $path")
    }
  }
}

object CommandType extends Enumeration {
  val Client, Server = Value
}
