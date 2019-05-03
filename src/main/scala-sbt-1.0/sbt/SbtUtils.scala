package sbt

import java.lang.ProcessBuilder.Redirect
import java.lang.{ProcessBuilder => JProcessBuilder}
import java.io.OutputStream

import sbt.internal.util.JLine
import sbt.io.Using
import sjsonnew.{BasicJsonProtocol, JsonFormat}

import scala.sys.process.{BasicIO, Process, ProcessIO}
import scala.util.Try

object SbtUtils extends BasicJsonProtocol {

  def download(url: URL, file: File): Unit = {
    Using.urlInputStream(url) { input =>
      IO.transfer(input, file)
    }
  }

  def executeTrapExit(f: => Unit, log: Logger): Try[Unit] = Run.executeTrapExit(f, log)

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

  private def terminalIO: ProcessIO = BasicIO.standard(SbtUtils.inTerminal)

  private def inTerminal: OutputStream => Unit = { out =>
    try { BasicIO.transferFully(JLine.createReader().getInput, out) }
    catch { case _: InterruptedException => () }
  }

  def hashAllFiles(files: Seq[File]): String = files.map(file => if (file.exists()) Hash.toHex(Hash(file)) else "").mkString("")

  def anyFileChanged[O : JsonFormat](inCacheFile: File, outCacheFile: File)(value: => O): Seq[File] => O = {
    val f = Tracked.inputChanged(inCacheFile) {
      (inChanged: Boolean, in: String) =>
        val outCache = Tracked.lastOutput[String, O](outCacheFile) {
          case (_, Some(out)) if !inChanged => out
          case _                            => value
        }
        outCache(in)
    }
    hashAllFiles _ andThen f
  }

}
