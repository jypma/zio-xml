package com.github.zioxml

import scala.xml.NodeSeq
import zio.test.Assertion
import zio.test.Assertion.Render._
import scala.xml.XML
import org.xml.sax.SAXParseException
import scala.xml.Utility.trim

object XmlAssertions {
  def isXmlString(expected: NodeSeq): Assertion[String] = {
    Assertion.assertion("isXmlString")(param(expected)) { actualString =>
      try {
        val actual = XML.loadString(actualString)
        // Structural mismatch can happen when comparing trimmed elements, so we .toString() first
        trim(actual.head).toString == trim(expected.head).toString
      } catch {
        case _:SAXParseException => false
      }
    }
  }

  def isXml(expected: NodeSeq): Assertion[NodeSeq] = {
    Assertion.assertion("isXml")(param(expected)) { actual =>
      // Structural mismatch can happen when comparing trimmed elements, so we .toString() first
      trim(actual.head).toString == trim(expected.head).toString
    }
  }
}
