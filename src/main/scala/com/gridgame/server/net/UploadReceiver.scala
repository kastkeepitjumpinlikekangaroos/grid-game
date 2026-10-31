package com.gridgame.server.net

import io.netty.buffer.ByteBuf
import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.timeout.{IdleStateEvent, IdleStateHandler}
import io.netty.util.ReferenceCountUtil

import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.{ScheduledFuture, TimeUnit}
import java.util.zip.CRC32

/**
 * What a connection becomes once it is given over to one upload (a problem report's, through
 * [[UploadReceiver.takeOver]]): exactly `expected` raw bytes, no frames, written to `file` as they
 * come with a running CRC-32. It ends once, with [[UploadReceiver.Received]] (all of them, and their
 * CRC), [[UploadReceiver.TooMuch]] (more than that came), or [[UploadReceiver.Cut]] (the connection
 * closed, went quiet for `idleMs`, or outlasted `deadlineMs`, before all of them came); the file is
 * deleted unless all of it came. `onEnd` is called on the channel's event loop, and is what answers
 * the client and closes the connection.
 *
 * It runs on the event loop: the writes are a TLS record's worth at a time, into the page cache.
 * The file is made as the receiver is (it throws if it can't be), so a server that can't write one
 * says so before the client is told to send.
 */
final class UploadReceiver(expected: Long, file: Path, onEnd: UploadReceiver.End => Unit,
                           deadlineMs: Long = UploadReceiver.DeadlineMs) extends ChannelInboundHandlerAdapter {
  import UploadReceiver._

  private val out: FileChannel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
  private val crc = new CRC32()
  private var received = 0L
  private var ended = false
  private var deadline: ScheduledFuture[_] = _

  /** Bytes taken in so far. */
  def bytesReceived: Long = received

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    deadline = ctx.executor().schedule(new Runnable {
      def run(): Unit = end(ctx, Cut(s"not all of it came within ${deadlineMs / 1000}s"))
    }, deadlineMs, TimeUnit.MILLISECONDS)
    if (expected == 0L) end(ctx, Received(0))
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case buf: ByteBuf =>
      try {
        if (!ended) {
          val n = buf.readableBytes()
          if (received + n > expected) end(ctx, TooMuch)
          else {
            for (b <- buf.nioBuffers()) {
              crc.update(b.duplicate())
              while (b.hasRemaining) out.write(b)
            }
            received += n
            if (received == expected) end(ctx, Received(crc.getValue.toInt))
          }
        }
      } catch {
        case e: java.io.IOException => end(ctx, Cut(s"couldn't be written: ${e.getMessage}"))
      } finally buf.release()
    case other => ReferenceCountUtil.release(other)
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit = evt match {
    case _: IdleStateEvent => end(ctx, Cut("the connection went quiet"))
    case _ => super.userEventTriggered(ctx, evt)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    end(ctx, Cut("the connection closed"))
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    end(ctx, Cut(s"the connection failed: ${cause.getMessage}"))
    ctx.close()
  }

  /** The upload is over, one way or another: the file kept only if all of it came. */
  private def end(ctx: ChannelHandlerContext, how: End): Unit = if (!ended) {
    ended = true
    // Nothing more is read: what came is all it is taken for
    ctx.channel().config().setAutoRead(false)
    if (deadline != null) deadline.cancel(false)
    try out.close() catch { case _: java.io.IOException => }
    how match {
      case Received(_) =>
      case _ => try Files.deleteIfExists(file) catch { case _: java.io.IOException => }
    }
    onEnd(how)
  }
}

object UploadReceiver {
  /** How long an upload may go without a byte. */
  val IdleMs: Long = 30000L
  /** How long an upload may take in all. */
  val DeadlineMs: Long = 10 * 60 * 1000L

  sealed trait End
  /** All of it came: its CRC-32. */
  final case class Received(crc: Int) extends End
  /** More came than it said it was. */
  case object TooMuch extends End
  /** It stopped before all of it came, and why. */
  final case class Cut(why: String) extends End

  /**
   * Give `channel`'s connection over to `receiver`: the rest of what it reads is the upload's bytes,
   * not frames, so the frame decoder goes, the TcpHandler (and with it any notion of a session) is
   * replaced by the receiver, and a read timeout of `idleMs` is put in front of it. What the server
   * writes still goes out through the length framing, as frames the client reads. The channel is
   * marked as an upload, so the login deadline doesn't close it. Run it on the channel's event loop,
   * before telling the client to send: bytes already read past the frame that asked go to the
   * receiver.
   */
  def takeOver(channel: Channel, receiver: UploadReceiver, idleMs: Long = IdleMs): Unit = {
    channel.attr(TcpHandler.Upload).set(java.lang.Boolean.TRUE)
    val pipeline = channel.pipeline()
    val tcp = pipeline.get(classOf[TcpHandler])
    if (tcp != null) pipeline.replace(tcp, ReceiverName, receiver) else pipeline.addLast(ReceiverName, receiver)
    pipeline.addBefore(ReceiverName, IdleName, new IdleStateHandler(idleMs, 0, 0, TimeUnit.MILLISECONDS))
    val decoder = pipeline.get(classOf[LengthFieldBasedFrameDecoder])
    if (decoder != null) pipeline.remove(decoder)
  }

  private val ReceiverName = "upload"
  private val IdleName = "upload-idle"
}
