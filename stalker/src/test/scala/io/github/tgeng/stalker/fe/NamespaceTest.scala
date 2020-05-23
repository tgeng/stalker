package io.github.tgeng.stalker.core.tt

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions
import io.github.tgeng.stalker.testing.UnitSpec

import io.github.tgeng.stalker.common.QualifiedName
import io.github.tgeng.stalker.core.common.Namespace
import io.github.tgeng.stalker.core.common.InMemoryNamespace

import QualifiedName.{_, given _}

class NamespaceTest extends UnitSpec {
  "InMemoryNamespace" - {
    val ns = InMemoryNamespace.createWithBuiltins("test.package")
    "builtins are present" in {
      assert(ns("Type").qn == qn("stalker.builtins.Type"))
      assert(ns("Level").qn == qn("stalker.builtins.Level"))
      assert(ns("lsuc").qn == qn("stalker.builtins.lsuc"))
      assert(ns("lmax").qn == qn("stalker.builtins.lmax"))
      assert(ns("Id").qn == qn("stalker.builtins.Id"))
      assert(ns("Refl").qn == qn("stalker.builtins.Id.Refl"))
    }

    "constructor name check works" in {
      assert(ns("Refl").constructorName == Some("Refl"))
    }

    "non-existent types indeed don't exist" in {
      assert(ns.get("blah").isLeft)
    }

    "read and write should work" in {
      ns("Foo") = "foo.Foo"
      assert(ns("Foo").qn == qn("foo.Foo"))
      val fooNs = InMemoryNamespace.create("foo.Foo")
      ns("Foo") = fooNs
      assert(ns("Foo") == fooNs)
      fooNs("Bar") = "foo.Foo.Bar"
      assert(ns("Foo.Bar").qn == qn("foo.Foo.Bar")) 
    }

    "render should be concise" in {
      ns("Foo") = "foo.Foo"
      assert(ns.render("foo.Foo") == "Foo")
    }

    "render should be concise for names in sub namespace" in {
      val sub = InMemoryNamespace.create("foo.bar")
      sub("Foo") = "foo.bar.Foo"
      ns("bar") = sub
      assert(ns.render("foo.bar.Foo") == "bar.Foo")
    }

    "render should output full name if not found " in {
      assert(ns.render("random.a.b.c") == ".random.a.b.c")
    }
  }

  private def (ns: Namespace) apply(name: String) = ns.get(ArraySeq.unsafeWrapArray(name.split('.'))) match {
    case Right(ns) => ns
    case Left(e) => throw IllegalArgumentException()
  }

  private def (ns: InMemoryNamespace) update(name: String, qn: QualifiedName) = ns(name) = InMemoryNamespace.create(qn)
}