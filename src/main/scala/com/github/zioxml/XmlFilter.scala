package com.github.zioxml

import zio.stream.ZPipeline
import zio.{Chunk, Ref, UIO, ZIO}

import XmlEvent._

object XmlFilter {
  /** Filters subtrees of nodes residing in the XML document at the direct ancestors given in [path].  The
    * subtrees will have the last element of [path] as their parent. Higher ancestors are filtered out.  For
    * example, filterSubtree("foo" :: "bar" :: Nil), given <xml><foo><bar>1</bar><hello/><bar>2</bar></xml>,
    * will emit events for <bar>1</bar><bar>2</bar>.
    */
  def filterSubtree(path: Seq[String]): ZPipeline[Any, Nothing, XmlEvent, XmlEvent] = {
    filter(new Filter {
      def nextMatchLevel(level: Int, matchLevel: Int, event: StartElement): Int = {
        if (matchLevel == level - 1 && matchLevel + 1 < path.size && event.localName == path(matchLevel + 1))
          matchLevel + 1
        else
          matchLevel
      }

      def nextMatchLevel(level: Int, matchLevel: Int, event: EndElement): Int = {
        if (matchLevel == level - 1)
          matchLevel - 1
        else
          matchLevel

      }

      def matched(matchLevel: Int): Boolean = {
        matchLevel >= path.size - 1
      }
    })
  }

  /** Filters subtrees of nodes in the XML with the given name, at any path.  The subtrees will have [tagName]
    * as their parent (ancestors are filtered out).
    */
  def filterTag(tagName: String): ZPipeline[Any, Nothing, XmlEvent, XmlEvent] = {
    filter(new Filter {
      def nextMatchLevel(level: Int, matchLevel: Int, event: StartElement): Int = {
        if (matchLevel == -1 && event.localName == tagName)
          level
        else
          matchLevel
      }

      def nextMatchLevel(level: Int, matchLevel: Int, event: EndElement): Int = {
        if (matchLevel != -1 && matchLevel == level - 1)
          -1
        else
          matchLevel

      }

      def matched(matchLevel: Int): Boolean = {
        matchLevel != -1
      }
    })
  }

  /** Removes nodes with the given name, and all of their children, from the stream. The node may occur at any
    * level. The rest of the stream is passed through unchanged. */
  def filterTagNot(tagName: String): ZPipeline[Any, Nothing, XmlEvent, XmlEvent] = {
    filter(new Filter {
      def nextMatchLevel(level: Int, matchLevel: Int, event: StartElement): Int = {
        if (matchLevel == -1 && event.localName == tagName)
          level
        else
          matchLevel
      }

      def nextMatchLevel(level: Int, matchLevel: Int, event: EndElement): Int = {
        if (matchLevel != -1 && matchLevel == level - 1)
          -1
        else
          matchLevel

      }

      def matched(matchLevel: Int): Boolean = {
        matchLevel == -1
      }
    })
  }

  private case class State(level: Int = 0, matchLevel: Int = -1) {
    def process(e: XmlEvent, filter: Filter): (Chunk[XmlEvent], State) = {
      val nextLevel = e match {
        case _:StartElement =>
          level + 1
        case _:EndElement =>
          level - 1
        case _ =>
          level
      }
      val nextMatchLevel = e match {
        case s:StartElement => filter.nextMatchLevel(level, matchLevel, s)
        case e:EndElement => filter.nextMatchLevel(level, matchLevel, e)
        case _ =>  matchLevel
      }
      val out = e match {
        case _:EndElement =>
          if (filter.matched(matchLevel)) Chunk(e) else Chunk.empty
        case _ if filter.matched(nextMatchLevel) =>
          Chunk(e)
        case _ =>
          Chunk.empty
      }
      (out, State(nextLevel, nextMatchLevel))
    }
  }

  private trait Filter {
    def nextMatchLevel(level: Int, matchLevel: Int, event: StartElement): Int
    def nextMatchLevel(level: Int, matchLevel: Int, event: EndElement): Int
    def matched(matchLevel: Int): Boolean
  }

  private def filter(f: Filter): ZPipeline[Any, Nothing, XmlEvent, XmlEvent] = {
    ZPipeline.fromPush {
      for {
        state <- Ref.make(State()).toManaged_
      } yield {
        def push: Option[Chunk[XmlEvent]] => UIO[Chunk[XmlEvent]] = _ match {
          case None =>
            ZIO.succeed(Chunk.empty)
          case Some(chunk) =>
            for {
              s <- state.get
              (newState, out) = chunk.foldLeft((s, Chunk.empty: Chunk[XmlEvent])){ (t, event) =>
                val (out, state) = t._1.process(event, f)
                (state, t._2 ++ out)
              }
              _ <- state.set(newState)
           } yield out
        }

        push
      }
    }
  }

}
