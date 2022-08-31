package com.github.zioxml

import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._
import XmlEvent._

object XmlIndenterSpec extends ZIOSpecDefault {
  override def spec = suite("XmlIndenter")(
    suite("indentOnly")(
      test("should indent an already-trimmed XML document") {
        val alreadyTrimmed = Seq(
          StartElement("tag",List(),None,None,List()),
          Characters("Hello,\n"),
          Characters("  world!"),
          StartElement("sub1",List(),None,None,List()),
          Characters("The best"),
          EndElement("sub1"),
          StartElement("sub2",List(),None,None,List()),
          Characters("Test"),
          EndElement("sub2"),
          EndElement("tag"),
        )
        for {
          res <- ZStream.fromIterable(alreadyTrimmed).via(XmlIndenter.indentOnly()).runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("tag"),
            Characters("Hello,\n"),
            Characters("  world!"),
            StartElement("sub1"),
            Characters("The best"),
            EndElement("sub1"),
            Characters("\n  "),
            StartElement("sub2"),
            Characters("Test"),
            EndElement("sub2"),
            Characters("\n"),
            EndElement("tag"),
          )))
        }
      },

      test("should not insert a starting newline for a document fragment") {
        val alreadyTrimmed = Seq(
          StartElement("tag",List(),None,None,List()),
          Characters("Hello,\n"),
          Characters("  world!"),
          StartElement("sub1",List(),None,None,List()),
          Characters("The best"),
          EndElement("sub1"),
          EndElement("tag")
        )

        for {
          res <- ZStream.fromIterable(alreadyTrimmed).via(XmlIndenter.indentOnly()).runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("tag"),
            Characters("Hello,\n"),
            Characters("  world!"),
            StartElement("sub1"),
            Characters("The best"),
            EndElement("sub1"),
            Characters("\n"),
            EndElement("tag")
          )))
        }
      }
    )
  )
}
