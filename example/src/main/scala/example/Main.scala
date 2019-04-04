package example

import java.io.File
import java.io.PrintWriter
import scala.io.Source

import eta.example.Transform

object Main extends App {
  val writer = new PrintWriter(new File("output.json"))

  Source.fromResource("data.json").getLines.foreach { json =>
    writer.write(Transform.fixJson(json) ++ "\n")
  }
  writer.close()

  val test = Transform.testBinomial()

  println(s"Hello: $test")
}
