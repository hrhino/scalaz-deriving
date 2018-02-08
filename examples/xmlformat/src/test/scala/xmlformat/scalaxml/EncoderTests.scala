// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package xmlformat
package scalaxml

import scalaz._

import java.time.Instant

import scala.collection.immutable.ListSet
import scala.concurrent.duration._

import org.scalatest.{ Tag => _, _ }
import org.scalatest.Matchers._

import Encoder.ops._

class EncoderTests extends FreeSpec {
  implicit class Helper(x: XNode) {
    def encode: xml.NodeSeq = x.toScalaXml
    def print: String       = encode.toString
  }

  "XNode Encoder" - {
    "should support XText" in {
      XText("wibble").encode shouldBe xml.Text("wibble")

      XText("<wibble>").print shouldBe "&lt;wibble&gt;"
    }

    "should support XCdata" in {
      XCdata("wibble").encode shouldBe xml.Text("wibble")

      XCdata("<wibble>").print shouldBe "<![CDATA[<wibble>]]>"
    }

    "should support nested CDATA" in {
      val raw = "<Foo><![CDATA[%s]]></Foo>"

      // a reminder that the PCData constructor escapes nested CDATA...
      xml.PCData(raw).data shouldBe "<Foo><![CDATA[%s]]]]><![CDATA[></Foo>"

      XCdata("<Foo><![CDATA[%s]]></Foo>").encode shouldBe xml.PCData(raw)
    }

    "should support XAtom" in {
      XAtom("foo").encode shouldBe xml.Unparsed("foo")

      // because, scala.xml...
      XAtom("foo").encode shouldBe xml.Text("foo")
    }

    "should support XTag" in {
      val tag = XTag(XAtom("foo"), IList.empty, XText("wibble"))

      tag.encode shouldBe xml.Elem(
        null,
        "foo",
        xml.Null,
        xml.TopScope,
        true,
        xml.Text("wibble")
      )

      tag.print shouldBe "<foo>wibble</foo>"
    }

    "should support XTag with children" in {
      val tag = XTag(XAtom("foo"), IList.empty, XText("wibble"))

      tag.encode shouldBe
        xml.Elem(
          null,
          "foo",
          xml.Null,
          xml.TopScope,
          true,
          xml.Text("wibble")
        )

      tag.print shouldBe "<foo>wibble</foo>"
    }

    "should support XTag with children and attributes" in {
      val tag = XTag(
        XAtom("foo"),
        IList(XAttr(XAtom("bar"), XText("<wobble>"))),
        XText("wibble")
      )

      tag.encode shouldBe xml.Elem(
        null,
        "foo",
        new xml.UnprefixedAttribute("bar", xml.Text("<wobble>"), xml.Null),
        xml.TopScope,
        true,
        xml.Text("wibble")
      )

      tag.print shouldBe """<foo bar="&lt;wobble&gt;">wibble</foo>"""
    }

    "should support XChildren" in {
      val list = XChildren(
        IList(
          XTag(
            XAtom("foo"),
            IList(XAttr(XAtom("bar"), XText("<wobble>"))),
            XText("wibble")
          ),
          XTag(
            XAtom("bar"),
            IList.empty,
            XText("wobble")
          )
        )
      )

      list.encode shouldBe xml.Group(
        Seq[xml.Node](
          xml.Elem(
            null,
            "foo",
            new xml.UnprefixedAttribute("bar", xml.Text("<wobble>"), xml.Null),
            xml.TopScope,
            true,
            xml.Text("wibble")
          ),
          xml.Elem(
            null,
            "bar",
            xml.Null,
            xml.TopScope,
            true,
            xml.Text("wobble")
          )
        )
      )

      list.print shouldBe """<foo bar="&lt;wobble&gt;">wibble</foo><bar>wobble</bar>"""

    }

  }

  implicit class AnyHelper[A: XEncoder](x: A) {
    import XEncoder.ops._

    def encode: xml.NodeSeq = x.toXml.toScalaXml
    def print: String       = encode.toString
  }

  "XML Encoder" - {
    "should support Boolean" in {
      true.encode shouldBe new xml.Atom("true")
      false.encode shouldBe new xml.Atom("false")
    }

    "should support integers" in {
      val expected = new xml.Atom("13")

      13.toShort.encode shouldBe expected
      13.toInt.encode shouldBe expected
      13.toLong.encode shouldBe expected

      13.toLong.print shouldBe "13"
    }

    "should support floating point numbers" in {
      val expected = new xml.Atom("0.1")

      0.1.toFloat.encode shouldBe expected
      0.1.toDouble.encode shouldBe expected

      0.1.toDouble.print shouldBe "0.1"
    }

    "should support single characters" in {
      'c'.encode shouldBe new xml.Text("c")
      'c'.print shouldBe "c"
    }

    "should support Strings" in {
      "<wibble><wobble".encode shouldBe new xml.Text("<wibble><wobble")
      "<wibble><wobble".print shouldBe "&lt;wibble&gt;&lt;wobble"
    }

    "should support Symbols" in {
      'foo.print shouldBe "foo"
    }

    "should special-case Option" in {
      (Some("hello"): Option[String]).print shouldBe "hello"

      (None: Option[String]).encode shouldBe xml.Group(Nil)
      (None: Option[String]).print shouldBe ""
    }

    "should special-case Either" in {
      (Left("hello"): Either[String, Int]).print shouldBe "hello"
      (Right(13): Either[String, Int]).print shouldBe "13"
    }

    "should support Traversables" in {
      Seq(1, 2, 3).print shouldBe "<value>1</value><value>2</value><value>3</value>"
      Set(1, 2, 3).print shouldBe "<value>1</value><value>2</value><value>3</value>"
      ListSet(1, 2, 3).print should (be(
        "<value>1</value><value>2</value><value>3</value>"
      )
        or be("<value>3</value><value>2</value><value>1</value>"))

      List(1, 2, 3).print shouldBe "<value>1</value><value>2</value><value>3</value>"
    }

    "should special case Map[Thing, OtherThing]" in {
      Map(1 -> "a", 2 -> "b", 3 -> "c").print shouldBe "<entry><key>1</key><value>a</value></entry><entry><key>2</key><value>b</value></entry><entry><key>3</key><value>c</value></entry>"
    }

    "should support FiniteDuration" in {
      10.seconds.print shouldBe "10000"
    }

    "should support Instant" in {
      val iso     = "2013-05-30T23:38:23.085Z"
      val instant = Instant.parse(iso)
      instant.print shouldBe iso
    }

    "should support generic products" in {
      import examples._

      Foo("hello").print shouldBe "<s>hello</s>"
      Caz.print shouldBe ""
      Baz.print shouldBe "Baz!"
      Faz(Some("hello")).print shouldBe "<o>hello</o>"
    }

    "should support generic coproducts" in {
      import examples._

      (Foo("hello"): SimpleTrait).print shouldBe "<Foo><s>hello</s></Foo>"
      (Caz: SimpleTrait).print shouldBe "<Caz/>"
      (Baz: SimpleTrait).print shouldBe "<Baz>Baz!</Baz>"

      (Wobble("fish"): AbstractThing).print shouldBe ("<Wobble><id>fish</id></Wobble>")

      (Wibble: AbstractThing).print shouldBe "<Wibble/>"
    }

    "should support generic recursive ADTs" in {
      import examples._

      val rec = Recursive("hello", Some(Recursive("goodbye")))
      rec.print shouldBe "<h>hello</h><t><h>goodbye</h><t/></t>"
    }

    "should encode fields as XmlAttribute" in {
      import examples._

      MultiField("hello", Tag("goodbye")).print shouldBe """<MultiField b="goodbye"><a>hello</a></MultiField>"""

      (MultiField("hello", Tag("goodbye")): MultiFieldParent).print shouldBe """<MultiField b="goodbye"><a>hello</a></MultiField>"""

    }
  }

}