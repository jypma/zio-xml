package com.github.zioxml

import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._
import XmlEvent._

object XmlTrimmerSpec extends ZIOSpecDefault {
  override def spec = suite("XmlTrimmer")(
    suite("trim")(
      test("should trim leading and trailing whitespace, removing whitespace-only Character events") {
        for {
          res <- ZStream.fromIterable("""
<tag>
  Hello!
  <sub1>
    The best
  </sub1>
  <sub2>Test</sub2>
</tag>
""".getBytes("UTF-8"))
          .via(XmlParser.parser())
          .via(XmlTrimmer.trim)
          .runCollect
        } yield {
          assert(res)(equalTo(Chunk(
            StartElement("tag",List(),None,None,List()),
            Characters("\n  Hello!\n  "),
            StartElement("sub1",List(),None,None,List()),
            Characters("\n    The best\n  "),
            EndElement("sub1"),
            StartElement("sub2",List(),None,None,List()),
            Characters("Test"),
            EndElement("sub2"),
            EndElement("tag"),
          )))
        }
      },

      test("should not touch whitespace on multi-Character boundaries") {
        val alreadyTrimmed = Chunk(
          StartElement("tag",List(),None,None,List()),
          Characters("Hello,\n"),
          Characters("  world!"),
          StartElement("sub1",List(),None,None,List()),
          Characters("The best"),
          EndElement("sub1"),
          EndElement("tag"),
        )
        for {
          res <- ZStream.fromChunk(alreadyTrimmed)
          .via(XmlTrimmer.trim)
          .runCollect
        } yield {
          assert(res)(equalTo(alreadyTrimmed))
        }
      }
    )
  )
}
