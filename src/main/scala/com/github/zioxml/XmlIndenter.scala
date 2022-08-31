package com.github.zioxml

import zio.Chunk
import zio.stream.ZPipeline
import XmlEvent._

object XmlIndenter {

  /** Indents a stream of XML parse events (removing any previous indentation first) */
  def indent(amount: Int = 2): ZPipeline[Any, Nothing, XmlEvent, XmlEvent] =
    XmlTrimmer.trim >>> indentOnly(amount)

/** Indents a stream of XML events. The incoming stream is assumed to have already been whitespace-trimmed by
  * XMLTrimFlow. Specifically, this means all Character events are assumed to have at least one non-whitespace
  * character (and, hence, will not be indented). */
  def indentOnly(amount: Int = 2): ZPipeline[Any, Nothing, XmlEvent, XmlEvent] = {
    case class State(depth: Int = 0, started: Boolean = false, hadCharacters: Boolean = false) {
      def process(event: XmlEvent): (State, Chunk[XmlEvent]) = {
        event match {
          case s:StartElement =>
            val prefix = if (!started || hadCharacters) Chunk.empty else {
              Chunk(Characters("\n" + (" " * (amount * depth))))
            }
            (copy(started = true, hadCharacters = false, depth = depth + 1), prefix :+ s)
          case c:Characters =>
            (copy(started = true, hadCharacters = true), Chunk(c))
          case e:EndElement =>
            // On end element, we need an indent if there have been any Characters
            // for this tag, i.e. if [indented] is actually FALSE.
            val d = depth - 1
            val prefix = if (hadCharacters) Chunk.empty else {
              Chunk(Characters("\n" + (" " * (amount * d))))
            }
            (copy(depth = d, hadCharacters = false), prefix :+ e)
          case e =>
            (this, Chunk(e))
        }
      }
    }

    StatefulPipeline.of[XmlEvent](State())(_ process _)(_ => Chunk.empty)
  }
}
