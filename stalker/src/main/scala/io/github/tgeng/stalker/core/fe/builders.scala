package io.github.tgeng.stalker.core.fe

import io.github.tgeng.common.nullOps
import io.github.tgeng.stalker.common.Namespace
import io.github.tgeng.stalker.common.LocalTfCtx
import io.github.tgeng.stalker.core.fe.ftConversion.{given _, _}
import io.github.tgeng.stalker.core.fe.pprint.toBlock
import io.github.tgeng.stalker.core.tt.contextOps
import io.github.tgeng.stalker.core.tt._
import io.github.tgeng.stalker.core.tt.typing.level
import io.github.tgeng.stalker.core.tt.reduction.toWhnf
import io.github.tgeng.parse._
import io.github.tgeng.parse.string.{given _, _}

import parser._

object builders {
  inline def [T](ctx: StringContext) ft() : FTerm = ft(ctx.parts(0))

  inline def [T](ctx: StringContext) t()(using LocalFtCtx, LocalTfCtx)(using Namespace) : Term = t(ctx.parts(0))

  inline def [T](ctx: StringContext) b()(using LocalFtCtx, LocalTfCtx, Context)(using Namespace, Signature) : Binding[Type] = b(ctx.parts(0))

  def ft(s: String) : FTerm = (whitespaces >> term << whitespaces << eof).parse(s) match {
    case Right(t) => t
    case Left(e) => throw Exception("Parsing FTerm failed:\n" + e.toStringWithInput(s))
  }

  def t(s: String)(using LocalFtCtx, LocalTfCtx)(using Namespace) : Term = ft(s).toTt match {
    case Right(t) => t
    case Left(e) => throw Exception(e.toBlock.toString)
  }

  def b(s: String)(using LocalFtCtx, LocalTfCtx, Context)(using Namespace, Signature) : Binding[Type] = {
    (whitespaces >> binding << whitespaces << eof).parse(s) match {
      case Right(b) => 
        (for b <- b.toTt
             _ <- b.ty.level
             _A <- b.ty.toWhnf
        yield Binding(_A)(b.name)) match {
          case Right(b) => b
          case Left(e) => throw Exception(e.toBlock.toString)
        }
      case Left(e) => throw Exception("Parsing binding failed:\n" + e.toStringWithInput(s))
    }
  }

  def tele(bindings: (LocalFtCtx, LocalTfCtx, Context) ?=> (Namespace, Signature) ?=> Binding[Type]*)(using Namespace, Signature) : Telescope = {
    val localIndices = LocalFtCtx()
    val localNames = LocalTfCtx()
    var context = Context.empty
    var result : Telescope = Nil
    for (b <- bindings) {
      val binding = b(using localIndices, localNames, context)
      result = binding :: result
      localIndices.add(binding.name)
      localNames.add(binding.name)
      context += binding
    }
    result
  }

  inline def [T](ctx: StringContext) q() : List[FCoPattern] = q(ctx.parts(0))

  def q(s: String) : List[FCoPattern] = (whitespaces >> coPatterns << whitespaces << eof).parse(s) match {
    case Right(q) => q
    case Left(e) => throw Exception("Parsing copatterns failed:\n" + e.toStringWithInput(s))
  }

  inline def [T](ctx: StringContext) decl() : FDeclaration = decl(ctx.parts(0).trim.!!.stripMargin)

  def decl(s: String) : FDeclaration = (declaration << eof).parse(s) match {
    case Right(d) => d
    case Left(e) => throw Exception("Parsing declaration failed:\n" + e.toStringWithInput(s))
  }

  def withBindings[T](bindings: (LocalFtCtx, LocalTfCtx, Context) ?=> (Namespace, Signature) ?=> Binding[Type]*)(action: (LocalFtCtx, LocalTfCtx, Context) ?=> T)(using Namespace, Signature) : T = {
    val localIndices = LocalFtCtx()
    val localNames = LocalTfCtx()
    var context = Context.empty
    for (b <- bindings) {
      val binding = b(using localIndices, localNames, context)
      localIndices.add(binding.name)
      localNames.add(binding.name)
      context += binding
    }
    action(using localIndices, localNames, context)
  }

  inline def [T](ctx: StringContext) cmd() : Seq[ModuleCommand] = cmd(ctx.parts(0).trim.!!.stripMargin)

  def cmd(s: String) : Seq[ModuleCommand] = (moduleCommands << eof).parse(s) match {
    case Right(c) => c
    case Left(e) => throw Exception("Parsing module command failed:\n" + e.toStringWithInput(s))
  }
}