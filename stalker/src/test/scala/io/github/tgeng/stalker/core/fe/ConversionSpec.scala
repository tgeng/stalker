package io.github.tgeng.stalker.core.fe

import scala.language.implicitConversions
import io.github.tgeng.stalker.core.testing.CoreSpec
import io.github.tgeng.stalker.core.tt._
import io.github.tgeng.stalker.core.tt.stringBindingOps
import io.github.tgeng.stalker.core.common.LeafNamespace

import Term._
import Whnf._
import Elimination._

class ConversionSpec extends CoreSpec {

  namespace("Vector") = LeafNamespace("stalker.collection.Vector")
  namespace("Nat") = LeafNamespace("stalker.util.Nat")
  namespace("String") = LeafNamespace("stalker.util.String")
  namespace("Integer") = LeafNamespace("stalker.util.Integer")

  "FTerm -> Term" - {
    "basic terms" in {
      assert(fterm("Type").tt == TRedux("stalker.builtins.Type", Nil))
      assert(fterm("5lv").tt == TWhnf(WLevel(5, Set.empty)))
      assert(fterm("con{}").tt == TWhnf(WCon("con", Nil)))
      assert(fterm("(A : Type) -> Type").tt == TWhnf(WFunction("A" ∷ TRedux("stalker.builtins.Type", Nil), TRedux("stalker.builtins.Type", Nil))))
      assert(fterm("(A : Type) -> A").tt == TWhnf(WFunction("A" ∷ TRedux("stalker.builtins.Type", Nil), TWhnf(WVar(0, Nil)))))
    }

    "more complex terms" in {
      assert(fterm("(n : Nat) -> (A : Type 0lv) -> Vector n A").tt == 
        TWhnf(WFunction(
          "n" ∷ TRedux("stalker.util.Nat", List()),
          TWhnf(WFunction(
            "A" ∷ TRedux("stalker.builtins.Type", List(ETerm(TWhnf(WLevel(0,Set()))))),
            TRedux("stalker.collection.Vector", List(ETerm(TWhnf(WVar(1,List()))), ETerm(TWhnf(WVar(0,List()))))))))))
      assert(fterm("con{Nat, String, Integer}").tt == 
        TWhnf(WCon("con",List(
          TRedux("stalker.util.Nat", List()), 
          TRedux("stalker.util.String", List()), 
          TRedux("stalker.util.Integer", List())))))
      assert(fterm("con{Nat -> Nat, String -> String, (n : Nat) -> (A : Type 0lv) -> Vector n A}").tt == 
        TWhnf(WCon("con", List(
          TWhnf(WFunction("" ∷ TRedux("stalker.util.Nat", List()), TRedux("stalker.util.Nat", List()))),
          TWhnf(WFunction("" ∷ TRedux("stalker.util.String", List()), TRedux("stalker.util.String", List()))),
          TWhnf(WFunction("n" ∷ TRedux("stalker.util.Nat", List()),
            TWhnf(WFunction("A" ∷ TRedux("stalker.builtins.Type", List(ETerm(TWhnf(WLevel(0, Set()))))),
              TRedux("stalker.collection.Vector", List(ETerm(TWhnf(WVar(1, List()))), ETerm(TWhnf(WVar(0, List())))))))))))))
    }
  }

  "Term -> FTerm" - {
    "basic terms" in {
      assert(TRedux("stalker.builtins.Type", Nil).fe == fterm("Type"))
      assert(TWhnf(WFunction("" ∷ TRedux("stalker.builtins.Type", Nil), TRedux("stalker.builtins.Type", Nil))).fe == fterm("Type -> Type"))
      assert(TWhnf(WLevel(5, Set.empty)).fe == fterm("5lv"))
      assert(TWhnf(WUniverse(TWhnf(WLevel(5, Set.empty)))).fe == fterm("Type 5lv"))
      assert(TWhnf(WLevelType).fe == fterm("Level"))
      assert(TWhnf(WData("a.b.c", Nil)).fe == fterm("a.b.c"))
      assert(TWhnf(WRecord("a.b.c", Nil)).fe == fterm("a.b.c"))
      assert(TWhnf(WId(TWhnf(WLevel(0, Set.empty)), TWhnf(WData("a.b.c", Nil)), TWhnf(WCon("con1", Nil)), TWhnf(WCon("con2", Nil)))).fe == fterm("Id 0lv a.b.c con1{} con2{}"))
      assert(TWhnf(WFunction("A" ∷ TRedux("stalker.builtins.Type", Nil), TWhnf(WVar(0, Nil)))).fe == fterm("(A : Type) -> A"))

      assert(TWhnf(WFunction(
          "n" ∷ TRedux("stalker.util.Nat", List()),
          TWhnf(WFunction(
            "A" ∷ TRedux("stalker.builtins.Type", List(ETerm(TWhnf(WLevel(0,Set()))))),
            TRedux("stalker.collection.Vector", List(ETerm(TWhnf(WVar(1,List()))), ETerm(TWhnf(WVar(0,List()))))))))).fe ==
        fterm("(n : Nat) -> (A : Type 0lv) -> Vector n A"))

      assert(TWhnf(WCon("con",List(
          TRedux("stalker.util.Nat", List()), 
          TRedux("stalker.util.String", List()), 
          TRedux("stalker.util.Integer", List())))).fe ==
        fterm("con{Nat, String, Integer}"))
      assert(TWhnf(WCon("con", List(
          TWhnf(WFunction("" ∷ TRedux("stalker.util.Nat", List()), TRedux("stalker.util.Nat", List()))),
          TWhnf(WFunction("" ∷ TRedux("stalker.util.String", List()), TRedux("stalker.util.String", List()))),
          TWhnf(WFunction("n" ∷ TRedux("stalker.util.Nat", List()),
            TWhnf(WFunction("A" ∷ TRedux("stalker.builtins.Type", List(ETerm(TWhnf(WLevel(0, Set()))))),
              TRedux("stalker.collection.Vector", List(ETerm(TWhnf(WVar(1, List()))), ETerm(TWhnf(WVar(0, List())))))))))))).fe ==
        fterm("con{Nat -> Nat, String -> String, (n : Nat) -> (A : Type 0lv) -> Vector n A}"))
    }
  }

  "FTerm <-> Term" - {
    "more more more!" in {
      roundtrip(
        """
        (vecFn : (n : Nat) -> (A : Type) -> Vector n A) ->
        (m : Nat) ->
        (n : Nat) ->
        Vector n (Vector m String)
        """,
        """
        (veryLongFn : (a : Nat) ->
                      (b : Integer) ->
                      (c : Nat -> Integer -> String) ->
                      (d : (n : Nat) -> Integer -> Vector n String) ->
                      Type) ->
        (a : Nat) ->
        (b : Integer) ->
        (c : Nat -> Integer -> String) ->
        (d : (n : Nat) -> Integer -> Vector n String) ->
        veryLongFn a b c d con1{a, b, c, d}
        """,
      )
    }
  }

  private def roundtrip(terms: String*) = {
    for (t <- terms) {
      val ft = fterm(t)
      assert(ft.tt.fe == ft)
    }
  }
}