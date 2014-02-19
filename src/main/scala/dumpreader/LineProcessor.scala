package dumpreader

import java.io.File
import scala.io.Source


class LineProcessor private (lineSource: Source, shutdownHook: => Unit ) {
  val lineItor = lineSource.getLines()
  var bufferedLine: Option[String] = None


  def readLine() : String = {
    if(!moreLines()) { shutdownHook; "" }
    else bufferedLine match {
      case Some(line) => bufferedLine = None; line
      case None => lineItor.next()
    }
  }

  def putReadLine(line: String) {
    assert(bufferedLine == None)
    bufferedLine = Some(line)
  }

  def moreLines() = lineItor.hasNext
}


object LineProcessor {
  def apply(file: File, shutdownHook: => Unit) : LineProcessor = {
    new LineProcessor(Source.fromFile(file), shutdownHook)
  }
}
