package com.github.zioxml

/**
  * Represents XML events, as they might appear in a reactive stream, loose modeled after StAX events.
  *
  * StartDocument and EndDocument as not modeled on purpose, since a stream already has established start and end
  * semantics.
  */
sealed trait XmlEvent

object XmlEvent {
  case class Namespace(uri: String, prefix: Option[String] = None)
  case class Attribute(name: String, value: String, prefix: Option[String] = None, namespace: Option[String] = None)

  case class StartElement(localName: String, attributes:Seq[Attribute] = Nil, prefix: Option[String] = None,
    namespace: Option[String] = None, namespaceCtx: Seq[Namespace] = Nil) extends XmlEvent
  case class EndElement(localName: String) extends XmlEvent
  case class ProcessingInstruction(target: Option[String], data: Option[String]) extends XmlEvent
  case class Characters(text: String) extends XmlEvent
  case class Comment(text: String) extends XmlEvent
  case class CData(text: String) extends XmlEvent
}
