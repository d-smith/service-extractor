package dumpreader

import java.io.File
import scala.io.Source


class LineProcessor private (lineSource: Source ) {
  val lineItor = lineSource.getLines()

  def readLine() : String = {
    lineItor.next()
  }

  def moreLines() = lineItor.hasNext
}


object LineProcessor {
  def apply(file: File) : LineProcessor = {
    new LineProcessor(Source.fromFile(file))
  }
}
