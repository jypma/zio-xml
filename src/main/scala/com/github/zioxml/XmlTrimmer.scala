package com.github.zioxml

import zio.Chunk
import zio.stream.ZPipeline
import XmlEvent.Characters

/** Removes Character events between two tag-events, if they're only whitespace. */
object XmlTrimmer {
  val trim: ZPipeline[Any, Nothing, XmlEvent, XmlEvent] = {
    case class State(buffer: Option[String] = None, haveContent: Boolean = false) {
      def process(event: XmlEvent): (State, Chunk[XmlEvent]) = {
        event match {
          case c:Characters if haveContent =>
            (this, Chunk(c))
          case Characters(s) if isWhitespace(s) =>
            (copy(buffer = Some(buffer.getOrElse("") + s)), Chunk.empty)
          case c:Characters =>
            (copy(haveContent = true, buffer = None), Chunk.fromIterable(buffer.toSeq.map(Characters(_))) :+ c)
          case event =>
            // Non-character event => we can throw away our whitespace-only buffer.
            (copy(haveContent = false, buffer = None), Chunk(event))
        }
      }

      def finish = Chunk.fromIterable(buffer.toSeq.map(Characters(_)))
    }

    StatefulPipeline.of[XmlEvent](State())(_ process _)(_.finish)
  }

  private val ws = "[ \\t\\n]*".r
  private def isWhitespace(s: String) = ws.matches(s)
}
