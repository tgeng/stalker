package io.github.tgeng.stalker.core.fe

import io.github.tgeng.common.extraSeqOps
import io.github.tgeng.stalker.common.QualifiedName
import io.github.tgeng.stalker.core.common.error._
import io.github.tgeng.stalker.core.tt.{Term => TtTerm, Elimination => TtElimination, Whnf, stringBindingOps, Binding => TtBinding}
import TtTerm.TWhnf
import Whnf._

case class Binding(name: String, ty: Term) {
  def tt(using ctx: NameContext) : Result[TtBinding[TtTerm]] = for {
    dbTerm <- ty.tt
  } yield TtBinding(dbTerm)(name)
}

enum Term {
  case TRedux(fn: QualifiedName, elims: List[Elimination])
  case TFunction(argName: String, argTy: Term, bodyTy: Term)
  case TUniverse(level: Int)
  case TData(qn: QualifiedName, params: List[Term])
  case TRecord(qn: QualifiedName, params: List[Term])
  case TId(ty: Term, left: Term, right: Term)
  case TVar(name: String, elims: List[Elimination])
  case TCon(con: String, args: List[Term])
  case TRefl

  def tt(using ctx: NameContext) : Result[TtTerm] = this match {
    case TRedux(fn, elims) => elims.liftMap(_.tt).map(TtTerm.TRedux(fn, _))
    case TFunction(argName, argTy, bodyTy) => for {
      dbArgTy <- argTy.tt
      dbBodyTy <- ctx.withName(argName) {
        bodyTy.tt
      }
    } yield TWhnf(WFunction(argName ∷ dbArgTy, dbBodyTy))
    case TUniverse(l) => Right(TWhnf(WUniverse(l)))
    case TData(qn, params) => params.liftMap(_.tt).map(p => TWhnf(WData(qn, p)))
    case TRecord(qn, params) => params.liftMap(_.tt).map(p => TWhnf(WRecord(qn, p)))
    case TId(ty, left, right) => for {
      dbTy <- ty.tt
      dbLeft <- left.tt
      dbRight <- right.tt
    } yield TWhnf(WId(dbTy, dbLeft, dbRight))
    case TVar(name, elims) => for {
      wElims <- elims.liftMap(_.tt)
      idx <- ctx.get(name)
    } yield TWhnf(WVar(idx, wElims))
    case TCon(con, args) => args.liftMap(_.tt).map(ts => TWhnf(WCon(con, ts)))
    case TRefl => Right(TWhnf(WRefl))
  }
}

enum Elimination {
  case ETerm(t: Term)
  case EProj(p: String)

  def tt(using ctx: NameContext) : Result[TtElimination] = this match {
    case ETerm(t) => t.tt.map(TtElimination.ETerm(_))
    case EProj(p) => Right(TtElimination.EProj(p))
  }
}
