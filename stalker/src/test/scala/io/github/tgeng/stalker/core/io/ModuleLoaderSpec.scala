package io.github.tgeng.stalker.core.io

import scala.language.implicitConversions
import io.github.tgeng.common.eitherOps
import io.github.tgeng.common.optionOps
import io.github.tgeng.testing.UnitSpec
import io.github.tgeng.stalker
import stalker.common.QualifiedName.{given _, _}
import stalker.core.fe.ModuleCommand._

class ModuleLoaderSpec extends UnitSpec {
  val pathResolver = PathResolver.createTmp(Nil)
  val moduleLoader = ModuleLoader(pathResolver)

  "loading nat module should work" in {
    val natModule = moduleLoader.loadModule("stalker.data.nat.base")
    val commands = natModule.!!!.!!!.commands
    val decls = commands.collect { case MDecl(d, _) => d }
    assert(decls.map(_.name).contains("Nat"))
    // Serialization does not work yet due to https://github.com/lampepfl/dotty/issues/9179

    // val moduleLoader2 = ModuleLoader(pathResolver)
    // val natModule2 = moduleLoader2.loadModule("stalker.data.nat.base")
    // assert(natModule == natModule2)
  }
}