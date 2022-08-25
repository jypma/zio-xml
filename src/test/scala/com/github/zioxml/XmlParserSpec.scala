package com.github.zioxml

import java.nio.charset.StandardCharsets

import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._

import Resources._
import XmlEvent._

object XmlParserSpec extends ZIOSpecDefault {
  override def spec = suite("XmlParser")(
    test("should parse a typical XML file") {
      for {
        res <- ZStream.fromChunk(Chunk.fromArray(resource("/UBL-Invoice-2.0-Example.xml").getBytes(StandardCharsets.UTF_8)))
          .via(XmlParser.parser())
          .runCollect
      } yield {
        assert(res)(hasSize(isGreaterThan(100))) &&
        assert(res)(startsWith(Seq(
          StartElement("Invoice",
            namespace = Some("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"),
            namespaceCtx = List(
              Namespace("urn:oasis:names:specification:ubl:schema:xsd:QualifiedDatatypes-2", Some("qdt")),
              Namespace("urn:oasis:names:specification:ubl:schema:xsd:CoreComponentParameters-2", Some("ccts")),
              Namespace("urn:oasis:names:specification:ubl:schema:xsd:DocumentStatusCode-1.0",Some("stat")),
              Namespace("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",Some("cbc")),
              Namespace("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",Some("cac")),
              Namespace("urn:un:unece:uncefact:data:draft:UnqualifiedDataTypesSchemaModule:2",Some("udt")),
              Namespace("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",None)))
        ))) &&
        assert(res)(endsWith(Seq(EndElement("Invoice"))))
      }
    }
  )
}
