package com.github.zioxml

import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._

import Resources._
import XmlAssertions._
import XmlEvent._

object XmlFilterSpec extends ZIOSpecDefault {
  val example = <xml><foo>1</foo><bar></bar><foo><tag/></foo><bar><foo/></bar></xml>

  override def spec = suite("XmlFilter")(
    suite("filterSubtree")(
      test("should select elements matching the path") {
        for {
          res <- ZStream.fromIterable((example).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterSubtree("xml" :: "foo" :: Nil))
            .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("foo"),
            Characters("1"),//
            EndElement("foo"),
            StartElement("foo"),
            StartElement("tag"),
            EndElement("tag"),
            EndElement("foo")
          )))
        }
      },

      test("should select invoice lines from an invoice") {
        for {
          res <- ZStream.fromIterable(resource("/UBL-Invoice-2.0-Example.xml").getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterSubtree("Invoice" :: "InvoiceLine" :: Nil))
            .runCollect
        } yield {
          assert(res)(hasSize(isGreaterThan(10))) &&
          assert(res)(startsWith(Chunk(StartElement("InvoiceLine", prefix = Some("cac"), namespace = Some("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2")))))
        }
      },

      test("should yield scala nodes when combined with XmlWriter.collectNode") {
        for {
          res <- ZStream.fromIterable((example).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterSubtree("xml" :: "foo" :: Nil))
            .via(XmlWriter.collectNode())
            .runCollect
        } yield {
          assert(res)(exists(isXml(<foo>1</foo>))) &&
          assert(res)(exists(isXml(<foo><tag/></foo>)))
        }
      },

      test("should combine with XmlWriter.collectNode when reading one chunk") {
        for {
          res <- ZStream.fromIterable(resource("/UBL-Invoice-2.0-Example.xml").toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .rechunk(10000000)
            .via(XmlFilter.filterSubtree("Invoice" :: "InvoiceLine" :: Nil))
            .via(XmlWriter.collectNode())
            .runCollect
        } yield {
          assert(res.size)(equalTo(2))
        }
      },

      test("should combine with XmlWriter.collectNode on arbitrary chunk splits") {
        for {
          res <- ZStream.fromIterable(resource("/UBL-Invoice-2.0-Example.xml").toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .rechunk(7)
            .via(XmlFilter.filterSubtree("Invoice" :: "InvoiceLine" :: Nil))
            .via(XmlWriter.collectNode())
            .runCollect
        } yield {
          assert(res.size)(equalTo(2))
        }
      },

      test("should combine with XmlWriter.collectNode on a Coupa result with chunks of size 1") {
        for {
          res <- ZStream.fromIterable(resource("/UBL-Invoice-2.0-Example.xml").toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .rechunk(1)
            .via(XmlFilter.filterSubtree("Invoice" :: "InvoiceLine" :: Nil))
            .via(XmlWriter.collectNode())
            .runCollect
        } yield {
          assert(res.size)(equalTo(2))
        }
      }
    ),

    suite("filterTag")(
      test("should select elements that match the tag name, wherever they are in the tree") {
        for {
          res <- ZStream.fromIterable((example).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterTag("foo"))
            .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("foo"),
            Characters("1"),
            EndElement("foo"),
            StartElement("foo"),
            StartElement("tag"),
            EndElement("tag"),
            EndElement("foo"),
            StartElement("foo"),
            EndElement("foo")
          )))
        }
      },

      test("should select more examples") {
        for {
          res <- ZStream.fromIterable((example).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterTag("bar"))
            .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("bar"),
            EndElement("bar"),
            StartElement("bar"),
            StartElement("foo"),
            EndElement("foo"),
            EndElement("bar")
          )))
        }
      },

      test("should match the root") {
        for {
          res <- ZStream.fromIterable((<root>1</root>).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterTag("root"))
            .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("root"),
            Characters("1"),
            EndElement("root")
          )))
        }
      }
    ),

    suite("filterTagNot")(
      test("should remove elements that match the tag name, wherever they are in the tree") {
        for {
          res <- ZStream.fromIterable((example).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterTagNot("foo"))
            .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("xml"),
            StartElement("bar"),
            EndElement("bar"),
            StartElement("bar"),
            EndElement("bar"),
            EndElement("xml")
          )))
        }
      },

      test("should remove more examples") {
        for {
          res <- ZStream.fromIterable((example).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterTagNot("bar"))
            .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("xml"),
            StartElement("foo"),
            Characters("1"),
            EndElement("foo"),
            StartElement("foo"),
            StartElement("tag"),
            EndElement("tag"),
            EndElement("foo"),
            EndElement("xml"),
          )))
        }
      },

      test("should remove the root, even though that yields an empty stream") {
        for {
          res <- ZStream.fromIterable((<root>1</root>).toString.getBytes("UTF-8"))
            .via(XmlParser.parser())
            .via(XmlFilter.filterTagNot("root"))
            .runCollect
        } yield {
          assert(res)(isEmpty)
        }
      }

    )

  )

}
