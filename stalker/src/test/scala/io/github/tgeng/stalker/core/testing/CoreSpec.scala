package io.github.tgeng.stalker.core.testing

import scala.language.implicitConversions
import org.scalatest.matchers._
import org.scalatest.Matchers

import io.github.tgeng.parse._
import io.github.tgeng.parse.string._
import io.github.tgeng.testing.UnitSpec
import io.github.tgeng.stalker
import stalker.core
import stalker.common._
import core.tt._
import core.fe._
import core.fe.builders._
import core.fe.pprint._

import matchers._
import Term._

class CoreSpec extends UnitSpec with Helpers {
  inline def (tm: Term) ∷ (ty: Term)(using LocalNames, Context, Signature, Namespace) = tm should haveType(ty)
  inline def (tm: Term) !∷ (ty: Term)(using LocalNames, Context, Signature, Namespace) = tm shouldNot haveType(ty)

  inline def (tm: Term) ~> (w: FTerm)(using LocalIndices, LocalNames, Context, Signature, Namespace) = tm should haveWhnf(w)
  inline def (tm: Term) !~> (w: FTerm)(using LocalIndices, LocalNames, Context, Signature, Namespace) = tm shouldNot haveWhnf(w)

  def (x: Term) ≡ (y: Term) = (new ≡(x, y), true)
  def (x: Term) ≢ (y: Term) = (new ≡(x, y), false)

  inline def (e: (≡[Term], Boolean)) ∷ (ty: Term)(using LocalIndices, LocalNames, Context, Signature, Namespace) = e match {
    case (e, true) => e should holdUnderType(ty)
    case (e, false) => e shouldNot holdUnderType(ty)
  }

  inline def (l1: Term) <= (l2: Term)(using LocalIndices, LocalNames, Context, Signature, Namespace) = l1 should beALowerOrEqualLevelThan(l2)
}