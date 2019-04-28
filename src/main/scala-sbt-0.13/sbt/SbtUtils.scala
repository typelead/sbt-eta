package sbt

import java.lang.ProcessBuilder.Redirect
import java.lang.{ProcessBuilder => JProcessBuilder}
import java.io.OutputStream

import scala.util.{Failure, Success, Try}

object SbtUtils {

  def download(url: URL, file: File): Unit = IO.download(url, file)

  def executeTrapExit(f: => Unit, log: Logger): Try[Unit] = {
    Run.executeTrapExit(f, log).map(msg => Failure(new MessageOnlyException(msg))).getOrElse(Success(()))
  }

  def execInherited(command: Seq[String], cwd: File): Int = {
    val jpb = new JProcessBuilder(command: _ *)
    jpb.directory(cwd)
    jpb.redirectInput(Redirect.INHERIT)
    Process(jpb).run(SbtUtils.terminalIO).exitValue()
  }

  def runInTerminal(cmd: => Unit, log: Logger): Try[Unit] = {
    JLine.usingTerminal { t =>
      t.init()
      executeTrapExit(cmd, log)
    }
  }

  private def terminalIO: ProcessIO = BasicIO.standard(SbtUtils.inTerminal, _ => true)

  private def inTerminal: OutputStream => Unit = { out =>
    try { BasicIO.transferFully(JLine.createReader().getInput, out) }
    catch { case _: InterruptedException => () }
  }

}
