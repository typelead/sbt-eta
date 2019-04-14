package com.typelead

trait Logger {

  def debug(s: String): Unit

  def info(s: String): Unit

  def warn(s: String): Unit

  def error(s: String): Unit

}

object Logger {

  private def f(s: String): String = "[etlas] " + s

  def apply(out: sbt.Logger): Logger = new Logger {

    def debug(s: String): Unit = out.debug(f(s))

    def info(s: String): Unit = out.info(f(s))

    def warn(s: String): Unit = out.warn(f(s))

    def error(s: String): Unit = out.error(f(s))

  }

  def apply(out: sbt.Keys.TaskStreams): Logger = new Logger {

    def debug(s: String): Unit = out.log.debug(f(s))

    def info(s: String): Unit = out.log.info(f(s))

    def warn(s: String): Unit = out.log.warn(f(s))

    def error(s: String): Unit = out.log.error(f(s))

  }

}