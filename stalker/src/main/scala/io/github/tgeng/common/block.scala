package io.github.tgeng.common

trait BlockConverter[T] {
  final def (t: T) pprint(widthLimit: Int = 80): String = {
    val sb = StringBuilder()
    t.toBlock.print(sb, widthLimit)
    sb.toString
  }

  def (t: T) toBlock: Block
}

enum WrapPolicy {
  case Wrap
  case NoWrap
  case ChopDown
  case AlwaysNewline
}

enum IndentPolicy {
  case FixedIncrement(amount: Int)
  case Aligned
}

enum DelimitPolicy {
  case Concat
  case Whitespace
  case Paragraph
}

import IndentPolicy._
import WrapPolicy._
import DelimitPolicy._

object Block {
  def apply(
    wrapPolicy: WrapPolicy = Wrap,
    indentPolicy: IndentPolicy = FixedIncrement(0),
    delimitPolicy: DelimitPolicy = Concat)
    (blocks: (Block | String)*) = new Block(blocks, wrapPolicy, indentPolicy, delimitPolicy)
}

case class Block(
    children: Seq[Block | String],
    wrapPolicy: WrapPolicy,
    indentPolicy: IndentPolicy,
    delimitPolicy: DelimitPolicy,
  ) {

  def ++ (more: Iterable[Block | String]) = Block(children ++ more, wrapPolicy, indentPolicy, delimitPolicy)
  def + (oneMore: Block | String) = Block(children :+ oneMore, wrapPolicy, indentPolicy, delimitPolicy)

  override def toString = {
    val sb = StringBuilder()
    print(sb, 80)
    sb.toString
  }

  def print(sb: StringBuilder, widthLimit: Int) : Unit = {
    print(using PrintContext.from(sb, widthLimit))
  }

  def print(using ctx: PrintContext) : Unit = {
    ctx.withIndent(indentPolicy) {
      val canFit = !width(ctx.widthLeft).isEmpty
      var first = true
      if ((canFit || wrapPolicy == NoWrap) && wrapPolicy != AlwaysNewline) {
        for (child <- children) {
          if (!first) {
            delimitPolicy match {
              case Whitespace => ctx.delimitWithSpace
              case Paragraph => child.delimitInParagraph
              case Concat => ()
            }
          }
          child.printBlockOrString
          first = false
        }
      } else {
        wrapPolicy match {
          case Wrap => {
            for (child <- children) {
              if (!first) {
                if (child.width(ctx.widthLeft, onlyMeasureFirstLine = true).isEmpty) {
                  ctx.delimitWithNewline
                  first = true
                } else {
                  delimitPolicy match {
                    case Whitespace => ctx.delimitWithSpace
                    case Paragraph => child.delimitInParagraph
                    case Concat => ()
                  }
                }
              }
              first = false
              child.printBlockOrString
            }
          }
          case ChopDown | AlwaysNewline => {
            for (child <- children) {
              if (!first || indentPolicy.isInstanceOf[FixedIncrement]) ctx.delimitWithNewline
              first = false
              child.printBlockOrString
            }
            if (indentPolicy.isInstanceOf[FixedIncrement]) ctx.nextBlockOnNewLine = true
          }
          case NoWrap => throw IllegalStateException()
        }
      }
    }
  }

  private def (b: Block | String) printBlockOrString(using ctx: PrintContext) = b match {
    case b: Block => b.print
    case s: String => ctx.append(s)
  }

  private def (b: Block | String) delimitInParagraph (using ctx: PrintContext) : Unit = if (!Set(',', '.', '!', '?', ';').contains(b.peek)) ctx.delimitWithSpace

  private def (b: Block | String) peek : Char = b match {
    case b: Block => b.children.headOption.map(_.peek).getOrElse(' ')
    case s: String => s.headOption.getOrElse(' ')
  }

  private def (b: Block | String) width(widthLeft: Int, onlyMeasureFirstLine: Boolean = false)(using ctx: PrintContext) : Option[Int] = b match {
    case s: String => if (s.size <= widthLeft) Some(s.size) else None
    case b@Block(children, wrapPolicy, indentPolicy, delimitPolicy) => {
      if (onlyMeasureFirstLine) {
        wrapPolicy match {
          case AlwaysNewline => return Some(0)
          case ChopDown => indentPolicy match {
            case FixedIncrement(_) => return Some(0)
            case Aligned => b.children.headOption match {
              case Some(cb) => return cb.width(widthLeft, true)
              case None => return Some(0)
            }
          }
          case Wrap => b.children.headOption match {
            case Some(cb) => return cb.width(widthLeft, true)
            case None => return Some(0)
          }
          case _ => ()
        } 
      }
      wrapPolicy match {
        case AlwaysNewline => return None
        case _ => ()
      }
      var width = 0
      var widthLeft2 = widthLeft
      for (child <- children) {
        var childWidth = child.width(widthLeft2) match {
          case Some(w) => w
          case None => return None
        }
        delimitPolicy match {
          case Whitespace | Paragraph => childWidth += 1
          case Concat => ()
        }
        width += childWidth
        widthLeft2 -= childWidth
      }
      delimitPolicy match {
        case Whitespace | Paragraph => Some(width - 1)
        case Concat => Some(width)
      }
    }
  }
}

class PrintContext(
  val sb: StringBuilder,
  private var width: Int,
  private val widthLimit: Int,
  private var indent: Int,
  var nextBlockOnNewLine: Boolean = false,
) {
  def widthLeft = widthLimit - width

  def append(s: String) = {
    if (nextBlockOnNewLine) {
      delimitWithNewline
      nextBlockOnNewLine = false
    }
    sb.append(s)
    width += s.size
  }

  def withIndent(indentPolicy: IndentPolicy)(action: => Unit) = {
    val currentIndent = indent
    indent = indentPolicy match {
      case FixedIncrement(n) => indent + n
      case Aligned => scala.math.max(width, indent)
    }
    action
    indent = currentIndent
  }

  def delimitWithNewline = {
    sb.append('\n')
    for(_ <- 0 until indent) { 
      sb.append(' ') 
    }
    width = indent
  }

  def delimitWithSpace = {
    sb.lastOption match {
      case Some(c) if !c.isWhitespace => {
        sb.append(' ')
        width += 1
      }
      case _ => ()
    }
  }

  def newlineSaving = scala.math.min(indent - width, 0)
}

object PrintContext {
  def from(sb: StringBuilder, widthLimit : Int = 80) = {
    val lineStart = sb.lastIndexOf('\n') + 1
    val width = sb.length - lineStart
    var indent = 0
    var i = lineStart
    while(i < sb.length && sb.charAt(i) == ' ') {
      indent += 1
      i += 1
    }
    PrintContext(sb, width, widthLimit, indent)
  }
}