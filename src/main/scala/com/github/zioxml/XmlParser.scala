package com.github.zioxml

import javax.xml.XMLConstants
import javax.xml.stream.{XMLInputFactory, XMLStreamConstants, XMLStreamException}

import scala.annotation.tailrec

import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.util.IllegalCharHandler.ReplacingIllegalCharHandler
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}
import zio.stream.ZPipeline
import zio.{Chunk, ZIO, ZManaged}

import XmlEvent._

// Loosely based on akka's wrapping of Aalto
object XmlParser {
  /**
    * Returns an XML parser that can parse an incoming byte stream into a series of XML events.
    * @param ignoreInvalidChars Whether to skip over (instead of fail) any XML-invalid characters in the stream
    */
  def parser(ignoreInvalidChars: Boolean = false): ZPipeline[Any, XMLStreamException, Byte, XmlEvent] = {
    val makeParser = ZManaged.succeed {
      val factory: AsyncXMLInputFactory = new InputFactoryImpl()

      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
      if (factory.isPropertySupported(XMLConstants.FEATURE_SECURE_PROCESSING)) {
        factory.setProperty(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      }

      val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = factory.createAsyncFor(Array.empty)
      if (ignoreInvalidChars) {
        parser.getConfig.setIllegalCharHandler(new ReplacingIllegalCharHandler(0))
      }

      parser
    }

    ZPipeline.fromPush {
      for {
        parser <- makeParser
      } yield {
        def reset(): ZIO[Any, Nothing, Chunk[XmlEvent]] = {
          // TODO reset parser and ready for next document (restart / switch ZManaged?)
          ZIO.succeed(Chunk.empty)
        }

        @tailrec def advanceParser(): ZIO[Any, Nothing, Chunk[XmlEvent]] = {
          if (parser.hasNext) {
            parser.next() match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                ZIO.succeed(Chunk.empty)

             //case XMLStreamConstants.START_DOCUMENT =>
             // ZIO.succeed(Chunk(StartDocument))

              case XMLStreamConstants.END_DOCUMENT =>
                reset() // .map(_ ++ Chunk(EndDocument))

              case XMLStreamConstants.START_ELEMENT =>
                val attributes = (0 until parser.getAttributeCount).map { i =>
                  val optNs = Option(parser.getAttributeNamespace(i)).filterNot(_ == "")
                  val optPrefix = Option(parser.getAttributePrefix(i)).filterNot(_ == "")
                  Attribute(name = parser.getAttributeLocalName(i),
                    value = parser.getAttributeValue(i),
                    prefix = optPrefix,
                    namespace = optNs)
                }.toList
                val namespaces = (0 until parser.getNamespaceCount).map { i =>
                  val namespace = parser.getNamespaceURI(i)
                  val optPrefix = Option(parser.getNamespacePrefix(i)).filterNot(_ == "")
                  Namespace(namespace, optPrefix)
                }.toList
                val optPrefix = Option(parser.getPrefix)
                val optNs = optPrefix.flatMap(prefix => Option(parser.getNamespaceURI(prefix)))
                ZIO.succeed(Chunk(StartElement(
                  parser.getLocalName,
                  attributes,
                  optPrefix.filterNot(_ == ""),
                  optNs.filterNot(_ == ""),
                  namespaceCtx = namespaces)))

              case XMLStreamConstants.END_ELEMENT =>
                ZIO.succeed(Chunk(EndElement(parser.getLocalName)))

              case XMLStreamConstants.CHARACTERS =>
                ZIO.succeed(Chunk(Characters(parser.getText)))

              case XMLStreamConstants.PROCESSING_INSTRUCTION =>
                ZIO.succeed(Chunk(ProcessingInstruction(Option(parser.getPITarget), Option(parser.getPIData))))

              case XMLStreamConstants.COMMENT =>
                ZIO.succeed(Chunk(Comment(parser.getText)))

              case XMLStreamConstants.CDATA =>
                ZIO.succeed(Chunk(CData(parser.getText)))

                // Do not support DTD, SPACE, NAMESPACE, NOTATION_DECLARATION, ENTITY_DECLARATION
                // ATTRIBUTE is handled in START_ELEMENT implicitly

              case _ =>
                advanceParser()
            }
          } else reset()
        }

        def advance(): ZIO[Any, XMLStreamException, Chunk[XmlEvent]] = try {
          advanceParser().flatMap { chunk1 =>
            if (chunk1.isEmpty) {
              ZIO.succeed(chunk1)
            } else {
              advance().map(chunk2 => chunk1 ++ chunk2)
            }
          }
        } catch {
          case x:XMLStreamException => ZIO.fail(x)
        }

        (optChunk: Option[Chunk[Byte]]) =>
          optChunk match {
            case None =>
              parser.getInputFeeder.endOfInput()
              if (!parser.hasNext) {
                reset()
              } else {
                advance()
              }

            case Some(chunk) =>
              val array = chunk.toArray
              parser.getInputFeeder.feedInput(array, 0, array.length)
              advance()
          }
      }
    }
  }

}
