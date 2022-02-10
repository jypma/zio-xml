# zio-xml

This library non-blocking parsers, writers and filters for handling streaming XML in the [zio](https://zio.dev/) Scala framework, specifically as `ZStream`. Parsing is done by wrapping the [Aalto](https://github.com/FasterXML/aalto-xml) XML parser. Writing uses the standard Java `XMLOutputFactory` mechanism (writing to a byte array which is known not to block).

Currently, ZIO 2.0+ is targeted.

## Model

A stream of XML is modeled as a `ZStream[Any, XMLStreamException, XmlEvent]`, where `XmlEvent` is a `sealed trait` that closely follows Java's own `XmlEvent` structure. A notable exception is that `StartDocument` and `EndDocument` as absent, since start- and end of a document is already indicated by stream semantics themselves.

## Parsing

The `XmlParser` object provides a `ZPipeline` that can turn bytes into XML events:

```scala
object XmlParser {
  def parser(ignoreInvalidChars: Boolean = false): ZPipeline[Any, XMLStreamException, Byte, XmlEvent]
}
```

If you have a `ZStream[Any, Nothing, Byte]`, you can feed that into the pipeline as follows:

```scala
val myStream: ZStream[Any, Nothing, Byte] = ???
val events: ZStream[Any, XMLStreamException, XmlEvent] = myStream >>> XmlParser.parser()
```

## Writing

Several ways are available to turn XML events back into bytes or DOM-like data structures.

### Writing to a document tree

Two `ZPipeline` variants exist that emit a document tree after a tag (and children) has been written:

```scala
object XmlWriter {
  def collectNode(): ZPipeline[Any, XMLStreamException, XmlEvent, scala.xml.Node]
  def collectElement(): ZPipeline[Any, XMLStreamException, XmlEvent, org.w3c.dom.Element]
}
```

The former emits a Scala XML `Node`, the latter emits a DOM `Element`. Use the variant that matches other libraries you're working with.

### Writing to bytes

You can also just write XML back to bytes, using another `ZPipeline` in `XmlWriter`.

```scala
object XmlWriter {
  def writeDocument(charset: Charset = StandardCharsets.UTF_8): ZPipeline[Any, XMLStreamException, XmlEvent, Byte]
  def writeFragment(charset: Charset = StandardCharsets.UTF_8): ZPipeline[Any, XMLStreamException, XmlEvent, Byte]
}
```

Two variants are available. You'll pick one depending on whether you plan to write a single document (`writeDocument`) or potentially multiple root nodes as an XML fragment (`writeFragment`).

## Filtering

In addition to parsing and writing, a few filters are presented that have proven useful as glue logic. See their ScalaDoc for details. Combined with `XmlWriter.collectNode`, they can be used to gather up pieces of a large XML stream for piece-meal further processing.

```scala
object XmlFilter {
  /** Filters subtrees of nodes residing in the XML document at the direct ancestors given in [path].  The
    * subtrees will have the last element of [path] as their parent. Higher ancestors are filtered out.  For
    * example, filterSubtree("foo" :: "bar" :: Nil), given <xml><foo><bar>1</bar><hello/><bar>2</bar></xml>,
    * will emit events for <bar>1</bar><bar>2</bar>.
    */
  def filterSubtree(path: Seq[String]): ZPipeline[Any, Nothing, XmlEvent, XmlEvent]


  /** Filters subtrees of nodes in the XML with the given name, at any path.  The subtrees will have [tagName]
    * as their parent (ancestors are filtered out).
    */
  def filterTag(tagName: String): ZPipeline[Any, Nothing, XmlEvent, XmlEvent]

  /** Removes nodes with the given name, and all of their children, from the stream. The node may occur at any
    * level. The rest of the stream is passed through unchanged. */
  def filterTagNot(tagName: String): ZPipeline[Any, Nothing, XmlEvent, XmlEvent]
}
```
