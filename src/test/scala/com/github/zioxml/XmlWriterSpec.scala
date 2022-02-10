package com.github.zioxml

import java.nio.charset.StandardCharsets

import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._

import XmlEvent._
import XmlAssertions._
import Resources._

object XmlWriterSpec extends DefaultRunnableSpec {
  override def spec = suite("XmlWriter")(
    suite("writeDocument")(
      testM("should write XML events to bytes") {
        for {
          res <- ZStream(StartElement("foo", Attribute("hello", "world") :: Nil), EndElement("foo"))
          .via(XmlWriter.writeDocument())
          .runCollect
        } yield {
          assert(new String(res.toArray))(isXmlString(<foo hello="world"/>))
        }
      },

      testM("should not get confused by starting characters") {
        for {
          res <- ZStream(Characters("\n"), StartElement("foo", Attribute("hello", "world") :: Nil), EndElement("foo"))
          .via(XmlWriter.writeDocument())
          .runCollect
        } yield {
          assert(new String(res.toArray))(isXmlString(<foo hello="world"/>))
        }
      },
      testM("should produce the same XML as XmlParser has read") {
        for {
          res <- ZStream.fromChunk(Chunk.fromArray(resource("/UBL-Invoice-2.0-Example.xml").getBytes(StandardCharsets.UTF_8)))
          .via(XmlParser.parser())
          .via(XmlWriter.writeDocument())
          .runCollect
        } yield {
          assert(new String(res.toArray))(isXmlString(resourceXml("/UBL-Invoice-2.0-Example.xml")))
        }
      },
      testM("should produce an XML declaration even if StartDocument is missing") {
        for {
          res <- ZStream(StartElement("foo", Attribute("hello", "world") :: Nil), EndElement("foo"))
          .via(XmlWriter.writeDocument())
          .runCollect
        } yield {
          assert(new String(res.toArray))(equalTo("<?xml version='1.0' encoding='UTF-8'?><foo hello=\"world\"/>"))
        }
      }
    ),

    suite("writeFragment")(
      testM("should not produce an XML declaration, even if StartDocument is present") {
        for {
          res <- ZStream(StartElement("foo", Attribute("hello", "world") :: Nil), EndElement("foo"))
          .via(XmlWriter.writeFragment())
          .runCollect
        } yield {
          assert(new String(res.toArray))(equalTo("<foo hello=\"world\"/>"))
        }
      },

      testM("should output multiple root nodes") {
        for {
          res <- ZStream(StartElement("foo"), EndElement("foo"), StartElement("bar"), EndElement("bar"))
          .via(XmlWriter.writeFragment())
          .runCollect
        } yield {
          assert(new String(res.toArray))(equalTo("<foo/><bar/>"))
        }
      }
    ),

    suite("collectElement")(
      testM("should collect top-level elements and children into Element instances") {
        for {
          res <- ZStream(
            StartElement("foo", Attribute("hello", "world") :: Nil),
              StartElement("bar"), EndElement("bar"),
            EndElement("foo"),
            StartElement("bar"), EndElement("bar")
          )
          .via(XmlWriter.collectElement())
          .runCollect
        } yield {
          assert(res)(hasSize(equalTo(2)))
        }
      }
    ),

    suite("collectNode")(
      testM("should collect top-level elements and children into Node instances") {
        for {
          res <- ZStream(
            StartElement("foo", Attribute("hello", "world") :: Nil),
              StartElement("bar"), EndElement("bar"),
            EndElement("foo"),
            StartElement("bar"), EndElement("bar")
          )
          .via(XmlWriter.collectNode())
          .runCollect
        } yield {
          assert(res)(exists(isXml(<foo hello="world"><bar/></foo>))) &&
          assert(res)(exists(isXml(<bar/>)))
        }
      }
    )
  )
}
