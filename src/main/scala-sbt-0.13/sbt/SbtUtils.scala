package sbt

import java.lang.ProcessBuilder.Redirect
import java.lang.{ProcessBuilder => JProcessBuilder}
import java.io.OutputStream

import sbt.serialization._

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

  def hashAllFiles(files: Seq[File]): String = files.map(file => if (file.exists()) Hash.toHex(Hash(file)) else "").mkString("")

  def anyFileChanged[O : Pickler : Unpickler](inCacheFile: File, outCacheFile: File)(value: => O): Seq[File] => O = {
    val f = Tracked.inputChangedWithJson(inCacheFile) {
      (inChanged: Boolean, in: String) =>
        val outCache = Tracked.lastOutputWithJson[String, O](outCacheFile) {
          case (_, Some(out)) if !inChanged => out
          case _                            => value
        }
        outCache(in)
    }
    hashAllFiles _ andThen f
  }

}
