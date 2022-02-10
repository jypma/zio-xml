package com.github.zioxml

import scala.xml.XML
import scala.xml.Node

object Resources {
  /** Loads the given classpath resource as a String */
  def resource(name: String): String = {
    new java.util.Scanner(getClass().getResourceAsStream(name), "UTF-8").useDelimiter("\\A").next()
  }

  def resourceXml(name: String): Node = XML.loadString(resource(name))
}
