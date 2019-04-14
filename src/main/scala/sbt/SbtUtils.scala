package sbt

import java.io.OutputStream

import sbt.internal.util.JLine

import scala.sys.process.{BasicIO, ProcessIO}
import scala.util.Try

object SbtUtils {

  def runInTerminal(cmd: => Unit, log: Logger): Try[Unit] = {
    JLine.usingTerminal { t =>
      t.init()
      Run.executeTrapExit(cmd, log)
    }
  }

  def terminalIO: ProcessIO = BasicIO.standard(SbtUtils.inTerminal)

  private def inTerminal: OutputStream => Unit = { out =>
    try { BasicIO.transferFully(JLine.createReader().getInput, out) }
    catch { case _: InterruptedException => () }
  }

}
