package io.github.tgeng.stalker.core.tt

import scala.language.implicitConversions
import scala.collection.mutable.ArrayBuffer
import io.github.tgeng.common._
import io.github.tgeng.common.extraIterableOps
import io.github.tgeng.common.extraSeqOps
import io.github.tgeng.common.extraSetOps
import io.github.tgeng.stalker.common._
import io.github.tgeng.stalker.common.Error._
import io.github.tgeng.stalker.common.LocalTfCtx
import reduction._
import typing._
import CaseTree._
import CoPattern._
import Pattern._
import Whnf._
import Term._
import UncheckedRhs._
import UResult._
import substitutionConversion.{given _}
import utils._

import debug._

extension elaboration on (p: Problem) {
  def elaborate(using clauses: ArrayBuffer[CheckedClause])(using Γ: Context)(using Σ: Signature) : Result[CaseTree] = p match {
    // Done
    case ((_E1, Nil) |-> rhs1) :: _ ||| (f, q̅) ∷ _C if _E1.solve.isRight => _E1.solve match {
      case Right(σ) => rhs1 match {
        case UTerm(v) => {
            val vσ = v.subst(σ)
            (vσ ∷ _C).check match {
            case Right(_) => {
              clauses.append(CheckedClause(Γ.toClosedTelescope, q̅, vσ, _C))
              Right(CTerm(vσ))
            }
            case Left(e) => Left(e)
          }
        }
        case _ => typingErrorWithCtx(e"False impossible case.")
      }
      case Left(_) => throw AssertionError()
    }
    // Intro
    case _P ||| (f, q̅) ∷ (WFunction(_A, _B)) => (for {
      wA <- _A.ty.toWhnf 
      r <- withCtxExtendedBy(_A.name ∷ wA) {
        for {
          wB <- _B.toWhnf
          _Pmod <- _P.shift(wA.raise(1))
          r <- (_Pmod ||| (f, q̅.map(_.raise(1)) :+ QPattern(PVar(0)(_A.name))) ∷ wB).elaborate
        } yield CLam(r)
      }
    } yield r)
    // Cosplit
    case _P ||| (f, q̅) ∷ (WRecord(qn, v̅)) => (for {
      record <- Σ getRecord qn
      fields <- record.getFields
      fieldCaseTrees <- fields.foldLeft[Result[Map[String, CaseTree]]](Right(Map())){ (acc, field) =>
        for {
          m <- acc
          _Pmod <- _P.filter(field.name, fields.map(_.name).toSet)
          wA <- field.ty.substHead(v̅ :+ TRedux(f, q̅.map(_.toElimination))).toWhnf
          q <- (_Pmod ||| (f, q̅ :+ QProj(field.name)) ∷ wA).elaborate
        } yield m ++ Map(field.name -> q)
      }
    } yield CRecord(fieldCaseTrees))
    case (_P@((_E1, q̅1) |-> rhs1) :: _) ||| (f, q̅) ∷ _C => {
      // Sort it so that we start splitting from the left most pattern.
      _E1.toSeq.sortBy {
        case ((TWhnf(WVar(x, Nil))) /? _) ∷ _ => -x
        case _ => 1
      }.findFirstEitherOption {
        // SplitCon
        case ((TWhnf(WVar(x, Nil))) /? (p@PCon(con, args))) ∷ (_A@WData(qn, v̅InΓ)) => for {
          data <- Σ getData qn
          (_Γ1 : Context /* required due to dotc bug */, _A1, _Γ2) = Γ.splitAt(x)
          ctxChangeOffset = -(_Γ2.size + 1)
          _ = assert(_A1.ty == _A.raise(ctxChangeOffset))
          cons <- data.getCons
          r <- withCtx(_Γ1) {
            val v̅ = v̅InΓ.map(_.raise(ctxChangeOffset))
            cons.liftMap { con =>
              for {
                _Δ <- con.allArgTys.substHead(v̅).toWhnfs
                r <- withCtxExtendedBy(_Δ) {
                  val ρ1 = Substitution.id[Pattern].drop(_Δ.size) ⊎ PCon(con.name, con.allArgTys.pvars.toList)
                  val ρ2 = ρ1.extendBy(_Γ2) 
                  for {
                    _P2 <- _P.subst(ρ2)
                    _Γ2mod <- _Γ2.subst(ρ1).toWhnfs
                    r <- withCtxExtendedBy(_Γ2mod) {
                      for {
                        wC <- _C.subst(ρ2).toWhnf
                        r <- (_P2 ||| (f, q̅.map(_.subst(ρ2))) ∷ wC).elaborate
                      } yield r
                    }
                  } yield r
                }
              } yield (con.name, r)
            }
          } 
        } yield Some(CDataCase(x, r.toMap))
        // SplitEq
        case ((TWhnf(WVar(x, Nil))) /? PRefl) ∷ (_A@WId(_, _BInΓ, uInΓ, vInΓ)) => for {
          wBInΓ <- _BInΓ.toWhnf
          (_Γ1 : Context /* required due to dotc bug */, _B1, _Γ2) = Γ.splitAt(x)
          ctxChangeOffset = -(_Γ2.size + 1)
          _ = assert(_B1.ty == _A.raise(ctxChangeOffset))
          r <- withCtx(_Γ1) {
            val wB = wBInΓ.raise(ctxChangeOffset)
            val u = uInΓ.raise(ctxChangeOffset)
            val v = vInΓ.raise(ctxChangeOffset)
            for {
              uResult <- ((u =? v) ∷ wB).unify
              r <- uResult match {
                case UPositive(_Γ1mod, ρ, τ) => {
                  for {
                    _Γ2mod <- _Γ2.subst(ρ).toWhnfs
                    ρmod = ρ.extendBy(_Γ2mod)
                    τmod = τ.extendBy(_Γ2mod)
                    _Pmod <- _P.subst(ρmod)
                    r <- withCtx(_Γ1mod + _Γ2mod) {
                      for {
                        wC <- _C.subst(ρmod).toWhnf
                        r <- (_Pmod ||| (f, q̅.map(_.subst(ρmod))) ∷ wC).elaborate
                      } yield CIdCase(x, τmod, r)
                    }
                  } yield r
                }
                case _ => typingErrorWithCtx(e"Cannot match $_A with refl because unification of $u and $v failed.")
              }
            } yield r
          }
        } yield Some(r)
        // SplitEmpty
        case ((TWhnf(WVar(x, Nil))) /? PAbsurd) ∷ _A => rhs1 match {
          case UImpossible => for {
            caseOption <- (x, _A).getEmptyCaseSplit
            r <- caseOption match {
              case Some(_Q) => Right(Some(_Q))
              case None => typingErrorWithCtx(e"The type inferencer failed to conclude $_A to be empty. Please prove it manually.")
            }
          } yield r
          case _ => typingErrorWithCtx(e"Absurd pattern should have an impossible rhs")
        }
        case _ => Right(None)
      }.flatMap {
        case Some(p) => Right(p)
        case None => typingErrorWithCtx(e"Elaboration failed when solving problem $p")
      }
    }
    // Split empty by detecting absurd pattern
    // TODO(tgeng): split on unit-like types to allow eta rule.
    case Nil ||| (f, q̅) ∷ _C => Γ.toTelescope.zipWithIndex.findFirstEitherOption {
      case (Binding(_A), x) => (x, _A).getEmptyCaseSplit
    }.flatMap {
      case Some(_Q) => Right(_Q)
      case None => typingErrorWithCtx(e"Missing branch...")
    }
  }
}

private def (_E: Set[(Term /? Pattern) ∷ Type]) solve(using Γ: Context)(using Σ: Signature) : Result[Substitution[Term]] = {
  _E.foldLeft(matched(Map.empty)){ case (acc, (w /? p) ∷ _A) =>
    for {
      σ1 <- acc
      σ2 <- w / p
      σ <- σ1 ⊎ σ2
    } yield σ
  }.flatMap {
    case Right(m) => {
      val σ = Substitution.from(m)
      // Check again to ensure forced patterns are correct.
      for _ <- _E.liftMap{ case (w /? p) ∷ _A => (p.toTerm.subst(σ) ≡ w ∷ _A).checkEq }
      yield σ
    }
    case _ => typingErrorWithCtx(e"Mismatch")
  }
}

private def (_P: UserInput) shift(_A: Type)(using Γ: Context): Result[UserInput] = _P match {
  case Nil => Right(Nil)
  case ((_E, QPattern(p) :: q̅) |-> rhs) :: _P => for {
    _Pmod <- _P.shift(_A)
  } yield ((_E.map{ case (w /? p) ∷ _B => (w.raise(1) /? p) ∷ _B.raise(1) } ++ Set((TWhnf(WVar(0, Nil)) /? p) ∷ _A) , q̅) |-> rhs) :: _Pmod
  case _ => typingErrorWithCtx(e"Unexpected clause")
}

private def (_P: UserInput) filter(fieldName: String, allFieldNames: Set[String])(using Γ: Context): Result[UserInput] = _P match {
  case Nil => Right(Nil)
  case ((_E, QProj(π) :: q̅) |-> rhs) :: _P => 
    if (allFieldNames.contains(π)) 
      for _Pmod <- _P.filter(fieldName, allFieldNames)
      yield 
        if (fieldName == π) ((_E, q̅) |-> rhs) :: _Pmod
        else _Pmod
    else typingErrorWithCtx(e"Unexpected field $π")
  case _ => typingErrorWithCtx(e"Unexpected clause")
}

private def (_P: UserInput) subst(σ: Substitution[Term])(using Σ: Signature): Result[UserInput] = _P match {
  case Nil => Right(Nil)
  case ((_E, q̅) |-> rhs) :: _P => for {
    _Es <- _E.liftMap {
      case (w /? p) ∷ _A => for {
        wA <- _A.subst(σ).toWhnf(using Context.empty)
        r <- ((w.subst(σ) /? p) ∷ wA).simpl
      } yield r
    }
    _Emod = unionAll(_Es)
    _Pmod <- _P.subst(σ)
  } yield _Emod match {
    case Some(_E) => ((_E, q̅) |-> rhs) :: _Pmod
    case None => _Pmod
  }
}

private def (candidate: (Int, Type)) getEmptyCaseSplit(using Γ: Context)(using Σ: Signature) : Result[Option[CaseTree]] = candidate match {
  case (x, WData(qn, _)) => for {
    data <- Σ getData qn
    cons <- data.getCons
  } yield cons.isEmpty match {
    case true => Some(CDataCase(x, Map.empty))
    case false => None
  }
  case (x, WId(_, _B, u, v)) => for {
    wB <- _B.toWhnf
    unifier <- ((u =? v) ∷ wB).unify
  } yield unifier match {
    case UNegative => Some(CDataCase(x, Map.empty))
    case _ => None
  }
  case _ => Right(None)
}

private def (constraint: (Term /? Pattern) ∷ Type) simpl(using Σ: Signature) : Result[Option[Set[(Term /? Pattern) ∷ Type]]] = {
  given Context = Context.empty
  constraint match {
    case (w /? p) ∷ _A => for {
      w <- w.toWhnf
      r <- (w, p, _A) match {
        case (WCon(c, v̅), PCon(c1, p̅), WData(qn, u̅)) => 
          if (c != c1) Right(None)
          else for {
            data <- Σ getData qn
            con <- data(c)
            _Δ <- con.allArgTys.substHead(u̅).toWhnfs
            _E <- (((v̅ ++ con.refls) /? (p̅ ++ con.pRefls)) ∷ _Δ).simplAll
          } yield _E
        case (WCon(c, v̅), PForcedCon(c1, p̅), WData(qn, u̅)) => 
          if (c != c1) typingErrorWithCtx(e"Mismatched forced constructor")
          else for {
            data <- Σ getData qn
            con <- data(c)
            _Δ <- con.allArgTys.substHead(u̅).toWhnfs
            _E <- (((v̅ ++ con.refls) /? (p̅ ++ con.pRefls)) ∷ _Δ).simplAll
          } yield _E
        case (WRefl, PRefl, WId(_, _, _, _)) => Right(Some(Set.empty[(Term /? Pattern) ∷ Type]))
        case _ => Right(Some(Set(constraint)))
      }
    } yield r
  }
}

private def (constraints: (List[Term] /? List[Pattern]) ∷ Telescope) simplAll(using Σ: Signature) : Result[Option[Set[(Term /? Pattern) ∷ Type]]] = {
  given Context = Context.empty
  constraints match {
    case (Nil /? Nil) ∷ Nil => Right(Some(Set.empty))
    case ((v :: v̅) /? (p :: p̅)) ∷ (_A :: _Δ) => for {
      _E1 <- ((v /? p) ∷ _A.ty).simpl
      _Δmod <- _Δ.substHead(v).toWhnfs
      _E2 <- ((v̅ /? p̅) ∷ _Δmod).simplAll
    } yield _E1 ∪⊥ _E2
  }
}

private def [T] (a: Option[Set[T]]) ∪⊥ (b: Option[Set[T]]) = (a, b) match {
  case (Some(a), Some(b)) => Some(a union b)
  case _ => None
}

private def unionAll[T](s: Set[Option[Set[T]]]) = s.fold[Option[Set[T]]](Some(Set.empty))(_ ∪⊥ _)

type Problem =  UserInput ||| (QualifiedName, List[CoPattern]) ∷ Type
type UserInput = List[(Set[(Term /? Pattern) ∷ Type], List[CoPattern]) |-> UncheckedRhs]

extension applicationTypingRelation on (app : (QualifiedName, List[CoPattern])) {
  def ∷ (A: Type) = new ∷(app, A)
}

extension userInputBarBarBar on (_P : UserInput) {
  def |||(a: (QualifiedName, List[CoPattern]) ∷ Type) = new |||(_P, a)
}

extension lhsOps on (lhs: (Set[(Term /? Pattern) ∷ Type], List[CoPattern])) {
  def |->(rhs: UncheckedRhs) = new |->(lhs, rhs)
}

extension termMatchingOps on (t: Term) {
  def /?(p: Pattern) = new /?(t, p)
}

extension termsMatchingOps on (ts: List[Term]) {
  def /?(ps: List[Pattern]) = new /?(ts, ps)
}

extension termMatchTypingRelation on (m: Term /? Pattern) {
  def ∷(_A: Type) = new ∷(m, _A)
}

extension termsMatchTypingRelation on (m: List[Term] /? List[Pattern]) {
  def ∷(_Δ: Telescope) = new ∷(m, _Δ)
}

case class |||[A, B](a: A, b: B) {
  override def toString = s"$a ||| $b"
}
case class |->[Lhs, Rhs](lhs: Lhs, rhs: Rhs) {
  override def toString = s"$lhs |-> $rhs"
}
case class /?[T, P](w: T, p: P) {
  override def toString = s"$w /? $p"
}
