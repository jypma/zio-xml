package com.github.zioxml

import java.io.ByteArrayOutputStream
import java.nio.charset.{Charset, StandardCharsets}
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.stream.{XMLOutputFactory, XMLStreamException, XMLStreamWriter}
import javax.xml.transform.dom.{DOMResult, DOMSource}
import javax.xml.transform.sax.SAXResult

import scala.util.Try
import scala.xml.Node
import scala.xml.parsing.NoBindingFactoryAdapter

import org.w3c.dom.{DOMException, Element}
import zio.stream.ZPipeline
import zio.{Chunk, IO, Ref, ZIO}

import XmlEvent._
import zio.Scope
import zio.ZLayer

object XmlWriter {
  /** Collects each root-level tag (and all children) into a Scala XML Node instance */
  def collectNode(): ZPipeline[Any, XMLStreamException, XmlEvent, Node] = {
    // TODO consider writing an adapter from XMLStreamWriter to ContentHandler,
    // so we can use scala.xml.parsing.NoBindingFactoryAdapter here, instead of creating
    // first one DOM tree, and then copying the whole tree into Scala's structure.
    collectElement() >>> ZPipeline.map(asScalaXml(_))
  }

  /** Collects each root-level tag (and all children) into a DOM Element instance */
  def collectElement(): ZPipeline[Any, XMLStreamException, XmlEvent, Element] = {
    writeTo(ZIO.succeed {
      val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
      val result = new DOMResult(doc)
      val output = XMLOutputFactory.newInstance().createXMLStreamWriter(result)
      (doc, output)
    }) { _ => Chunk.empty }{ doc =>
      doc.getDocumentElement() match {
        case null => Chunk.empty
        case elem => Chunk(elem)
      }
    }
  }

  /** Writes each element in the given charset to bytes, in a streaming fashion. Always writes an XML
    * declaration first, and only supports one root element. */
  def writeDocument(charset: Charset = StandardCharsets.UTF_8): ZPipeline[Any, XMLStreamException, XmlEvent, Byte] = {
    write(charset, startDoc = true)
  }

  /** Writes each element in the given charset to bytes, in a streaming fashion. Never writes an XML
    * declaration, and supports more than one root element. */
  def writeFragment(charset: Charset = StandardCharsets.UTF_8): ZPipeline[Any, XMLStreamException, XmlEvent, Byte] = {
    write(charset, startDoc = false)
  }

  private def write(charset: Charset, startDoc: Boolean): ZPipeline[Any, XMLStreamException, XmlEvent, Byte] = {
    writeTo(
      ZIO.succeed {
        val bos = new ByteArrayOutputStream
        val output = XMLOutputFactory.newInstance().createXMLStreamWriter(bos, charset.name())
        if (startDoc) {
          output.writeStartDocument()
        }
        (bos, output)
     }
    ){ bos =>
      val res = Chunk.fromArray(bos.toByteArray())
      bos.reset()
      res
    }{ _ => Chunk.empty }
  }

  private def writeTo[O,T]
    (makeOutput: ZIO[Scope, Nothing, (T, XMLStreamWriter)])
    (emitAfterEvent: T => Chunk[O])
    (emitAfterRoot: T => Chunk[O]): ZPipeline[Any, XMLStreamException, XmlEvent, O] = {

    def process(chunk: Chunk[XmlEvent], level: Int, output: XMLStreamWriter, bos: T,
      allowStartSplit: Boolean): (Chunk[O], Chunk[XmlEvent], Int) = {
      var l = level
      var startingNextDoc = false
      val firstStartElement = chunk.indexWhere(_.isInstanceOf[StartElement])
      val splitIdx = chunk.zipWithIndex.find { case (event, index) =>
        event match {
          case _:StartElement =>
            if (l == 0 && (allowStartSplit || index > firstStartElement)) {
              // Don't increment level here, since we'll be at level 0 on the next call.
              startingNextDoc = true
              true
            } else {
              l += 1
              false
            }
          case _:EndElement =>
            l -= 1
            false
          case _ =>
            false
        }
      }.map(_._2)

      val (now, later) = splitIdx.map(chunk.splitAt).getOrElse((chunk, Chunk.empty))

      now.foreach(e => writeEvent(output, e))
      val out = if (startingNextDoc) {
        output.writeEndDocument()
        val r1 = emitAfterEvent(bos)
        val r2 = emitAfterRoot(bos)
        r1 ++ r2
      } else emitAfterEvent(bos)

      (out, later, l)
    }

    ZPipeline.fromPush {
      ZIO.scoped {
        val switchable = SwitchableScope.make(makeOutput)(_ => ZIO.unit)
        for {
          output <- switchable
          levelRef <- Ref.make(0)
          scope <- ZIO.scope
        } yield {
          var lastChunk: Chunk[XmlEvent] = Chunk.empty

          def pushChunk(chunk: Chunk[XmlEvent], allowStartSplit: Boolean): ZIO[Scope, XMLStreamException, Chunk[O]] = for {
            t <- output.get
            (bos, out) = t
            level <- levelRef.get
            r <- ZIO.fromTry(Try(process(chunk, level, out, bos, allowStartSplit))).catchAll {
              case x:XMLStreamException =>
                ZIO.fail(x)
              case x => ZIO.die(x)
            }
            (out, remainder, newLevel) = r
            _ <- levelRef.set(newLevel)
            rest <- if (!remainder.isEmpty) output.switch *> pushChunk(remainder, allowStartSplit = false) else ZIO.succeed(Chunk.empty)
          } yield {
            lastChunk = chunk
            out ++ rest
          }

          var hadEvents = false
          def push: Option[Chunk[XmlEvent]] => IO[XMLStreamException, Chunk[O]] = optChunk =>
          optChunk match {
            case None =>
              output.get.map { case (bos, out) =>
                out.flush()
                val res = emitAfterEvent(bos) ++ emitAfterRoot(bos)
                res
              }

            case Some(chunk) =>
              for {
                level <- levelRef.get
                // We're allowed to start a new document at the very first event if we've
                // seen any events (i.e. we are mid-stream).
                res <- pushChunk(chunk, allowStartSplit = hadEvents || (level > 0)).provide(ZLayer.succeed(scope))
              } yield {
                hadEvents = true
                res
              }
          }

          push
        }
      }
    }
  }

  private def writeAttributes(output: XMLStreamWriter, attributes: Iterable[Attribute]): Unit =
    attributes.foreach { att =>
      att match {
        case Attribute(name, value, Some(prefix), Some(namespace)) =>
          output.writeAttribute(prefix, namespace, name, value)
        case Attribute(name, value, None, Some(namespace)) =>
          output.writeAttribute(namespace, name, value)
        case Attribute(name, value, Some(_), None) =>
          output.writeAttribute(name, value)
        case Attribute(name, value, None, None) =>
          output.writeAttribute(name, value)
      }
    }

  private def writeEvent(output: XMLStreamWriter, ev: XmlEvent): Unit =
  try {
    ev match {
      case StartElement(localName, attributes, optPrefix, Some(namespace), namespaceCtx) =>
        val prefix = optPrefix.getOrElse("")
        output.setPrefix(prefix, namespace)
        output.writeStartElement(prefix, localName, namespace)
        namespaceCtx.foreach(ns => output.writeNamespace(ns.prefix.getOrElse(""), ns.uri))
        writeAttributes(output, attributes)

      case StartElement(localName, attributes, Some(_), None, namespaceCtx) => // Shouldn't happened
        output.writeStartElement(localName)
        namespaceCtx.foreach(ns => output.writeNamespace(ns.prefix.getOrElse(""), ns.uri))
        writeAttributes(output, attributes)

      case StartElement(localName, attributes, None, None, namespaceCtx) =>
        output.writeStartElement(localName)
        namespaceCtx.foreach(ns => output.writeNamespace(ns.prefix.getOrElse(""), ns.uri))
        writeAttributes(output, attributes)

      case EndElement(_) =>
        output.writeEndElement()

      case Characters(text) =>
        output.writeCharacters(text)
      case ProcessingInstruction(Some(target), Some(data)) =>
        output.writeProcessingInstruction(target, data)

      case ProcessingInstruction(Some(target), None) =>
        output.writeProcessingInstruction(target)

      case ProcessingInstruction(None, Some(data)) =>
        output.writeProcessingInstruction(None.orNull, data)
      case ProcessingInstruction(None, None) =>
      case Comment(text) =>
        output.writeComment(text)

      case CData(text) =>
        output.writeCData(text)
    }
  } catch {
    case x:XMLStreamException => throw new XMLStreamException(s"While writing ${ev}", x)
    case x:DOMException => throw new XMLStreamException(s"While writing ${ev}", x)
  }

  private def asScalaXml(dom: _root_.org.w3c.dom.Node): Node = {
    val source = new DOMSource(dom)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
  }

}
