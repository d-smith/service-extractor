package dumpreader

import java.io.File
import scala.io.Source


class LineProcessor private (lineSource: Source ) {
  val lineItor = lineSource.getLines()
  var bufferedLine: Option[String] = None

  def readLine() : String = {
    bufferedLine match {
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
  def apply(file: File) : LineProcessor = {
    new LineProcessor(Source.fromFile(file))
  }
}
