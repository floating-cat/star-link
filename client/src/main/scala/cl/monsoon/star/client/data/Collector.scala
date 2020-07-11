package cl.monsoon.star.client.data

import java.net.URL
import java.nio.file.{Files, Paths}

import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Using

trait Collector {

  type Out
  val resPath: String
  val mapper: String => Out

  // Paths#get doesn't work for jar.
  lazy val outputUrl: URL = getClass.getResource(resPath)

  final def write(outputDir: Option[String], output: Iterator[String]): Unit = {
    val path = outputDir.fold(Paths.get(outputUrl.toURI))(Paths.get(_, resPath))
    Files.write(path, output.to(Iterable).asJava)
  }

  final def get(): List[Out] = {
    Using.resource(Source.fromURL(outputUrl)) { source =>
      source.getLines().map(mapper).toList
    }
  }
}
