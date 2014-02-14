package dumpreader

import java.io.File


object DumpProcessor extends App {
  import LineRouter._

  if(args.length != 1) throw new Error("Input file not specified")

  val lineProcessor = LineProcessor(new File(args(0)))

  while(lineProcessor.moreLines()) {
    val line = lineProcessor.readLine
    extractLineSpec(line) match {
      case Some(spec) =>
        val(request, lineNo) = spec
        if(lineNo == "1") {
          processStartOfNewRequest(request, line)
        } else if(applicationDataFollows(line)) {
          println("Heads up - data may follow")
          processApplicationData(request)
        }

      case None =>
    }
  }

  def processApplicationData(requestNo: String) {
    skipDashLine()
    if(!hasRequest(requestNo))
      processRequest(requestNo)
    else
      processResponse()
  }

  def processRequest(requestNo: String) {
    var appDataLine = lineProcessor.readLine()
    if(appDataLine.contains("Envelope")) {
      while(appDataLine.trim() != "") {
        routeLine(new RequestData(requestNo, appDataLine))
        appDataLine = lineProcessor.readLine()
      }
    }
  }

  def processResponse() {

  }

  def millisFromTimestamp(ts: String) : Long = {
    (java.lang.Double.valueOf(ts) * 1000).longValue()
  }

  def extractLineSpec(line: String) : Option[(String,String)] = {
    val splitLine = line.trim().split("\\s+")
    splitLine.length match {
      case x if x < 2 => None
      case _ =>
        if(allDigits(splitLine(0)) && allDigits(splitLine(1)))
          Some((splitLine(0), splitLine(1)))
        else
          None
    }
  }

  def allDigits(s: String) : Boolean = {
    import java.lang.Character.digit
    s.foldLeft(true) {
      (status, current) => if(digit(current, 10) == -1) false else status
    }
  }

  def processStartOfNewRequest(callNo: String, line: String) {
    val connLine = line.split("\\s+")
    val timestamp = millisFromTimestamp(connLine(2))
    routeLine(NewServiceCall(callNo, timestamp))
  }

  def applicationDataFollows(line: String) : Boolean = {
    if(line.trim().endsWith("application_data")) true else false
  }

  def skipDashLine() {
    lineProcessor.readLine()
  }
}
