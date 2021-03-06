import sbt._
import sbt.Keys._
import tut.Plugin._
import diagram.Plugin.DiagramKeys

object build extends Build with NpmCliBase {
  lazy val lintAll = taskKey[Unit]("lint text, html")
  lazy val testAll = taskKey[Unit]("test scala, links")
  lazy val buildWithCheck = taskKey[Unit]("lintAll testAll build")

  val scalazDiagram1 = TaskKey[Unit]("scalazDiagram1")
  val scalazDiagram2 = TaskKey[Unit]("scalazDiagram2")

  val scalazVersion = "7.2.0"

  def urlMap(clazz: Class[_]): Map[String, String] =
    if (clazz.getName.startsWith("scalaz"))
      Map(
        "href" -> s"https://github.com/scalaz/scalaz/tree/v${scalazVersion}/core/src/main/scala/${clazz.getName.replace('.', '/')}.scala",
        "fontsize" -> "10",
        "height" -> ".4",
        "width" -> ".4",
        "label" -> diagram.ClassNode.decodeClassName(clazz.getSimpleName).replace("\\", "\\\\"),
        "tooltip" -> clazz.getName
      )
    else
      Map()

  val weightMap: Map[(String, String), Int] = List(
    ("MonadError", "Monad", 3),
    ("Cobind", "Functor", 2),
    ("Traverse", "Foldable", 2)
  ).map{
    case (x, y, z) => ((x, y), z)
  }.toMap

  val root = project.in(file(".")).settings(
    tutSettings,
    tutSourceDirectory := srcDir,
    tutTargetDirectory := compiledSrcDir,
    GitBook.settings,
    TextLint.settings,
    LinkTest.settings,
    fullResolvers ~= {_.filterNot(_.name == "jcenter")},
    libraryDependencies <+= sbtDependency,
    libraryDependencies ++= (
      ("org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion) ::
      Nil
    ),
    diagram.Plugin.classDiagramSettings,
    DiagramKeys.classDiagramSetting ~= { s =>
      s.copy(
        name = s"scalaz $scalazVersion diagram",
        nodeSetting = clazz => s.nodeSetting(clazz) ++ urlMap(clazz),
        edgeSetting = (x, y) => {
          weightMap.get((x.getSimpleName, y.getSimpleName)).fold(s.edgeSetting(x, y)){ weight =>
            s.edgeSetting(x, y) + ("weight" -> weight.toString)
          }
        },
        commonNodeSetting = s.commonNodeSetting + ("target" -> "_blank")
      )
    },
    scalazDiagram1 := {
      val svg = DiagramKeys.classDiagramWrite.toTask(
        "MonadPlus ComonadStore Traverse1 IsEmpty MonadReader MonadState MonadError MonadListen Nondeterminism Divisible BindRec".split(' ').map{
          "scalaz." + _
        }.mkString(" ", " ", "")
      ).value
      IO.move(svg, file("gitbook") / "diagram1.svg")
    },
    scalazDiagram2 := {
      val svg = DiagramKeys.classDiagramWrite.toTask(
        "Enum Bitraverse Choice Arrow Associative ProChoice".split(' ').map{
          "scalaz." + _
        }.mkString(" ", " ", "")
      ).value
      IO.move(svg, file("gitbook") / "diagram2.svg")
    },
    TaskKey[Unit]("scalazDiagram") := Def.sequential(scalazDiagram1, scalazDiagram2).value,
    lintAll := Def.sequential(LinkTest.eslint, TextLint.textlint.toTask("")).value,
    testAll := Def.sequential(compile in Test, LinkTest.linkTest).value,
    buildWithCheck := Def.sequential(lintAll, testAll, GitBook.build)
  )
}
