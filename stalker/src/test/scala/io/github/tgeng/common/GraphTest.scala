package io.github.tgeng.common

import scala.language.implicitConversions
import io.github.tgeng.testing.UnitSpec

import graph._

class GraphTest extends UnitSpec {
  "testing collapseCycles" in {
    collapsing() becomes (Seq(), Map())
    collapsing(1 -> 2) becomes (Seq($(2), $(1)), Map($(1) -> $(2)))

    collapsing(
      1 -> 2,
      2 -> 3
    ) becomes(
      Seq($(3), $(2), $(1)),
      Map(
        $(1) -> $(2),
        $(2) -> $(3),
      ))

    collapsing(
      1 -> 2,
      2 -> 3,
      3 -> 1
    ) becomes(
      Seq($(1, 2, 3)),
      Map()
    )

    collapsing(
      1 -> (2, 3),
    ) becomes(
      Seq($(2), $(3), $(1)),
      Map($(1) -> ($(2), $(3)))
    )

    collapsing(
      1 -> (2, 3),
      2 -> (4),
      4 -> (2)
    ) becomes(
      Seq($(2, 4), $(3), $(1)),
      Map($(1) -> ($(2, 4), $(3)))
    )

    collapsing(
      1 -> (2, 3),
      2 -> 1,
      3 -> (4, 5),
      4 -> 3
    ) becomes(
      Seq($(5), $(3, 4), $(1, 2)),
      Map($(1, 2) -> $(3, 4), $(3, 4) -> $(5))
    )

    collapsing(
      1 -> (2, 3, 5),
      2 -> 1,
      3 -> (4, 5),
      4 -> 3
    ) becomes(
      Seq($(5), $(3, 4), $(1, 2)),
      Map($(1, 2) -> ($(3, 4), $(5)), $(3, 4) -> $(5))
    )

    collapsing(
      1 -> (10, 20, 30),
      10 -> 11,
      11 -> 12,
      12 -> 13,
      13 -> 10,
      20 -> 21,
      21 -> 22,
      22 -> 23,
      23 -> 20,
      30 -> 31,
      31 -> 32,
      32 -> 33,
      33 -> 30,
    ) becomes(
      Seq($(10, 11, 12, 13), $(20, 21, 22, 23), $(30, 31, 32, 33), $(1)),
      Map($(1) -> ($(10, 11, 12, 13), $(20, 21, 22, 23), $(30, 31, 32, 33)))
    )
  }

  inline def collapsing(entries: (Int, Set[Int])*) = Map(entries : _*)

  inline def (g: Map[Int, Set[Int]]) becomes (topoSorted: Seq[Set[Int]], dag: Map[Set[Int], Set[Set[Int]]]) = {
    val (actualTopoSorted, actualDag) = collapseCycles(g)
    assert(actualDag == dag)
    assert(actualTopoSorted == topoSorted)
  }

  def $[T](ts: T*) : Set[T] = Set(ts : _*)
  def [T](t: T) -> (deps: T*) = (t, deps.toSet)
}