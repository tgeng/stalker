package io.github.tgeng.stalker.core.fe

import scala.language.postfixOps
import scala.language.implicitConversions
import scala.collection.Map
import io.github.tgeng.common.extraSeqOps._
import io.github.tgeng.stalker.common.QualifiedName
import io.github.tgeng.stalker.common.Error._
import io.github.tgeng.parse._
import io.github.tgeng.parse.string.{given _, _}

object parser {
  import FTerm._
  import FElimination._

  private val bodyInvalidChars = " \\r\\n\\t()\\[\\]{}."
  private val bodyPattern = s"[^${bodyInvalidChars}]"
  private val headPattern = s"""[^`'"0-9${bodyInvalidChars}]"""
  private val num : Parser[FTerm] = P { 
    for i <- "[0-9]+".rp.map(s => Integer.parseInt(s))
        levelSuffix <- "lv".?
    yield levelSuffix match {
      case Some(_) => FTLevel(i)
      case None => FTNat(i)
    }
  }

  private def name(using opt: ParsingOptions) = P { 
    {
      var p = s"$headPattern$bodyPattern*".rp
      if (opt.skipWhereAtLineEnd) {
        p = p & not("where" << spaces << (newline | eof))
      }
      p
    }.withFilter(!Set("->", ":", "=", "_").contains(_)) 
  }

  private def names(using opt: ParsingOptions) = P { name sepBy1 '.' map (_.toList) }

  private def con(using opt: ParsingOptions)(using IndentRequirement) : Parser[FTerm] = P {
    for {
      n <- name
      args <- '{' >> whitespaces >> (atom sepBy whitespaces) << whitespaces << '}'
    } yield FTCon(n, args.toList)
  }

  private def ref(using opt: ParsingOptions)(using IndentRequirement) : Parser[FTerm] = P {
    for names <- names
    yield FTRedux(names, Nil)
  }

  private def atom(using opt: ParsingOptions)(using IndentRequirement) : Parser[FTerm] = P {
    '(' >>! whitespaces >> termImpl(using opt.copy(appDelimiter = whitespaces)) << whitespaces << ')' | con | ref | num
  }

  private def proj(using opt: ParsingOptions) = '.' >>! name

  private def elim(using opt: ParsingOptions)(using IndentRequirement) : Parser[FElimination] = P {
    atom.map(FETerm(_)) | proj.map(p => FEProj(p))
  }

  private def app(using opt: ParsingOptions)(using IndentRequirement) : Parser[FTerm] = P {
    lift(atom, (opt.appDelimiter >> elim).*).flatMap {
      case (t, elims) => if (elims.isEmpty) {
        pure(t)
      } else {
        (t, elims) match {
          case (FTRedux(names, es1), es2) => pure(FTRedux(names, es1 ++ es2))
          case (FTFunction(_, _), _) => fail("Cannot apply to function type.")
          case (FTLevel(_), _) => fail("Cannot apply to a level.")
          case (FTCon(_, _), _) => fail("Cannot apply to a constructed value.")
          case (FTNat(_), _) => fail("Cannot apply to a Nat.")
        }
      }
    }
  }

  private def bindingImpl(using opt: ParsingOptions)(using IndentRequirement) : Parser[FBinding] = P {
    lift(name << whitespaces, ':' >>! whitespaces >> termImpl).map((x, ty) => FBinding(x, ty))
  }

  private def namedArgTy(using opt: ParsingOptions)(using IndentRequirement) : Parser[FBinding] = P {
    '(' >> whitespaces >> bindingImpl << whitespaces << ')'
  }

  private def argTy(using opt: ParsingOptions)(using IndentRequirement) : Parser[FBinding] = P {
    namedArgTy | app.map(FBinding("", _))
  }

  private def termImpl(using opt: ParsingOptions)(using IndentRequirement) : Parser[FTerm] = P {
    for {
      bdn <- (argTy << whitespaces << "->" <<! whitespaces).?
      r <- bdn match {
        case Some(b) => for t <- termImpl(using opt) yield FTFunction(b, t)
        case None => app
      }
    } yield r
  }

  def term = P { termImpl(using ParsingOptions())(using IndentRequirement(0)) }
  def binding = P { bindingImpl(using ParsingOptions())(using IndentRequirement(0)) }

  import FPattern._
  import FCoPattern._

  private def pConRaw(using opt: ParsingOptions)(using IndentRequirement) : Parser[FPattern] = P {
    for forced <- "..".?
        con <- name
        args <- '{' >>! whitespaces >> (pAtom sepBy whitespaces) << whitespaces << '}'
    yield FPCon(con, args.toList, forced.isDefined)
  }

  private def pAtom(using opt: ParsingOptions)(using IndentRequirement) : Parser[FPattern] = P {
    pConRaw |
    name.map(FPVar(_)) |
    "()".as(FPAbsurd) |
    '(' >>! whitespaces >> pAtom(using opt.copy(appDelimiter = whitespaces)) << whitespaces << ')' |
    ".." >>! atom.map(FPForced(_))
  }

  private def qProj(using opt: ParsingOptions) : Parser[FCoPattern] = P {
    proj.map(FQProj(_))
  }

  private def qAtom(using opt: ParsingOptions)(using IndentRequirement) : Parser[FCoPattern] = P {
    pAtom.map(FQPattern(_)) | qProj
  }

  private def coPatternsImpl(using opt: ParsingOptions)(using IndentRequirement) : Parser[List[FCoPattern]] = P {
    qAtom sepBy spaces map (_.toList)
  }

  def coPatterns : Parser[List[FCoPattern]] = P {
    given ParsingOptions = ParsingOptions()
    given IndentRequirement = IndentRequirement(0)
    coPatternsImpl
  }

  import FDeclaration._

  private def constructor(typeName: String)(using opt: ParsingOptions)(using IndentRequirement) : Parser[FConstructor] = P {
    for n <- name
        _ <- spaces >> ':' <<! spaces
        argTys <- aligned {
          (argTy << whitespaces << "->" << whitespaces)
        }.*
        typeParams <- typeName >>! (spaces >> atom).*
    yield FConstructor(n, argTys.toList, typeParams.toList)
  }

  private def schemaType(using opt: ParsingOptions)(using IndentRequirement) : Parser[(Seq[FBinding], FTerm)] = P {
    for argTys <- namedArgTy sepBy whitespaces
        _ <- spaces >> ':' <<! spaces
        ty <- termImpl(using ParsingOptions(skipWhereAtLineEnd = true))
    yield (argTys, ty)
  }

  private def data(using opt: ParsingOptions)(using IndentRequirement) : Parser[FDeclaration] = P { 
    for n <- "data " >>! spaces >> name << spaces
        (argTys, ty) <- schemaType
        cons <- spaces >> whereSomething(constructor(n))
    yield FData(n, argTys.toList, ty, cons)
  }

  private def field(using opt: ParsingOptions)(using IndentRequirement) : Parser[FField] = P {
    for n <- name
        _ <- spaces >> ':' <<! spaces
        ty <- termImpl 
    yield FField(n, ty)
  }

  private def record(using opt: ParsingOptions)(using IndentRequirement) : Parser[FDeclaration] = P { 
    for n <- "record " >>! spaces >> name << spaces
        (argTys, ty) <- schemaType
        fields <- spaces >> whereSomething(field)
    yield FRecord(n, argTys.toList, ty, fields)
  }

  private def whereSomething[T](something: Parser[T]) : Parser[Seq[T]] = {
    for _ <- commitAfter("where")
        s <- (spaces >> someLines >> spaces >> something).*
    yield s
  }

  import FUncheckedRhs._

  private def clause(using opt: ParsingOptions)(using IndentRequirement) : Parser[FUncheckedClause] = P {
    for lhs <- coPatternsImpl
        rhs <- (spaces >> "=" >>! spaces >> termImpl).?
    yield rhs match {
      case Some(rhs) => FUncheckedClause(lhs, FUTerm(rhs))
      case None => FUncheckedClause(lhs, FUImpossible)
    }
  }

  private def definition(using opt: ParsingOptions)(using IndentRequirement) : Parser[FDeclaration] = P {
    for n <- "def " >>! spaces >> name << spaces << ":" <<! spaces
        ty <- termImpl
        clauses <- (spaces >> someLines >> spaces >> clause).*
    yield FDefinition(n, ty, clauses)
  }

  def declaration : Parser[FDeclaration] = P { 
    given ParsingOptions = ParsingOptions()
    withIndent(1) {
      data | record | definition
    }
  }

  import ModuleCommand._
  import Visibility._

  private def mImport : Parser[Seq[ModuleCommand]] = P {
    for {
      v <- visibility << whitespace | pure(Private)
      _ <- "import" << whitespace
      src <- names(using ParsingOptions())
      dst <- mImportExportDst(src)
    } yield v match {
      case Private => Seq(MNsOp(src, dst, Private))
      case Internal => Seq(MNsOp(src, dst, Private), MNsOp(src, dst, Internal))
      case Public => Seq(MNsOp(src, dst, Private), MNsOp(src, dst, Internal), MNsOp(src, dst, Public))
    }
  }

  private def mExport : Parser[Seq[ModuleCommand]] = P {
    for {
      isInternal <- ("internal" << whitespace).?.map(_.isDefined)
      _ <- "export" << whitespace
      src <- names(using ParsingOptions())
      dst <- mImportExportDst(src)
    } yield isInternal match {
      case true => Seq(MNsOp(src, dst, Internal))
      case false => Seq(MNsOp(src, dst, Internal), MNsOp(src, dst, Public))
    }
  }

  private def mImportExportDst(src: List[String]) : Parser[List[String]] = P {
    "._".map(_ => Nil) |
    spaces >> "as " >> names(using ParsingOptions()) |
    pure(List(src.last))
  }

  private def visibility : Parser[Visibility] = P {
    ("private" as Visibility.Private) |
    ("internal" as Visibility.Internal) |
    ("public" as Visibility.Public)
  }

  private def mDecl : Parser[Seq[ModuleCommand]] = P {
    for {
      v <- visibility << whitespace | pure(Public)
      decl <- declaration
    } yield Seq(MDecl(decl, v))
  }

  def moduleCommands : Parser[Seq[ModuleCommand]] = P {
    mImport | mExport | mDecl
  }

  def module : Parser[Module] = P {
    for commands <- moduleCommands sepBy whitespaces
    yield Module(commands.flatten)
  }
}

private case class ParsingOptions(val appDelimiter: Parser[?] = spaces, val skipWhereAtLineEnd: Boolean = false)
