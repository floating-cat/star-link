package cl.monsoon.star

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpConstants
import io.netty.util.ByteProcessor
import io.netty.util.internal.AppendableCharSequence

/**
 * See https://stackoverflow.com/q/57591379
 * Forked from [[https://github.com/netty/netty/blob/0fb58d3c542231d2e95a16a4182d46e7ebc293d1/codec-http/src/main/java/io/netty/handler/codec/http/HttpObjectDecoder.java#L859]]
 */
class HeaderParser extends ByteProcessor {

  private var size = 0
  // TODO
  // Max header in our WebSocket packet is 32 characters in length.
  private val seq: AppendableCharSequence = new AppendableCharSequence(32)

  def parse(buffer: ByteBuf): Cursor = {
    val oldSize = size
    seq.reset()
    val i = buffer.forEachByte(this)
    if (i == -1) {
      size = oldSize
      return Suspension
    }
    buffer.readerIndex(i + 1)
    size = 0

    if (seq.length() != 0) Value(seq)
    else End
  }

  @Override
  override def process(value: Byte): Boolean = {
    val nextByte = (value & 0xFF).toChar
    if (nextByte == HttpConstants.LF) {
      val len = seq.length
      // Drop CR if we had a CRLF pair
      if (len >= 1 && seq.charAtUnsafe(len - 1) == HttpConstants.CR) {
        size -= 1
        seq.setLength(len - 1)
      }
      return false
    }
    size += 1
    seq.append(nextByte)
    true
  }
}

sealed trait Cursor

case object Suspension extends Cursor

final case class Value(v: CharSequence) extends Cursor

case object End extends Cursor
