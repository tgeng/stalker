package io.github.tgeng.stalker.core.fe

import io.github.tgeng.common.extraSeqOps
import io.github.tgeng.stalker.core.tt.{Binding => DbBinding, Term => DbTerm, Pattern => DbPattern, CoPattern => DbCoPattern}

enum Pattern {
  case PVar(name: String)
  case PRefl
  case PCon(con: String, patterns: List[Pattern])
  case PForcedCon(con: String, patterns: List[Pattern])
  case PForced(t: Term)
  case PAbsurd
}

import Pattern._

extension patternOps on (self: Pattern) {
  def toDbPattern(using ctx: NameContext) : Result[DbPattern] = self match {
    case PVar(name) => ctx.get(name).map(DbPattern.PVar(_)) 
    case PRefl => Right(DbPattern.PRefl)
    case PCon(con, patterns) => patterns.liftMap(_.toDbPattern).map(DbPattern.PCon(con, _))
    case PForcedCon(con, patterns) => patterns.liftMap(_.toDbPattern).map(DbPattern.PForcedCon(con, _))
    case PForced(t) => t.toDbTerm.map(DbPattern.PForced(_))
    case PAbsurd => Right(DbPattern.PAbsurd)
  }
}

enum CoPattern {
  case QPattern(p: Pattern)
  case QProj(p: String)
}

import CoPattern._

extension coPatternOps on (self: CoPattern) {
  def toDbCoPattern(using ctx: NameContext) : Result[DbCoPattern] = self match {
    case QPattern(p) => p.toDbPattern.map(DbCoPattern.QPattern(_))
    case QProj(p) => Right(DbCoPattern.QProj(p))
  }
}