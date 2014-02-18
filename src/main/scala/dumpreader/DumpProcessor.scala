package dumpreader

import java.io.File
import org.slf4j.LoggerFactory


object DumpProcessor extends App {
  import LineRouter._

  val logger = LoggerFactory.getLogger(this.getClass)

  if(args.length != 1) throw new Error("Input file not specified")

  val lineProcessor = LineProcessor(new File(args(0)))

  while(lineProcessor.moreLines()) {
    val line = lineProcessor.readLine
     processLine(line)
  }

  def processLine(line: String) {
    extractLineSpec(line) match {
      case Some(spec) =>
        val(request, lineNo) = spec
        if(lineNo == "1") {
          processStartOfNewRequest(request, line)
        } else if(applicationDataFollows(line)) {
          val nextLine = lineProcessor.readLine()
          if(isDashLine(nextLine))
            processApplicationData(request)
          else
            processLine(nextLine)
        }

      case None =>
    }
  }


  def processApplicationData(requestNo: String) {
    if(!hasRequest(requestNo))
      processRequest(requestNo)
    else
      processResponse(requestNo)
  }

  def processRequest(requestNo: String) {
    var appDataLine = lineProcessor.readLine()
    if(appDataLine.contains("Envelope")) {
      var envelope2 = false
      var done = false
      while(!done) {
        routeLine(new RequestData(requestNo, appDataLine))
        appDataLine = " " + lineProcessor.readLine().trim()
        if(appDataLine.contains("Envelope") && !envelope2) envelope2 = true
        else if(appDataLine.trim() =="" && envelope2) done = true
      }
    }
  }

  def processResponse(requestNo: String) {
    var appDataLine = lineProcessor.readLine()
    if(appDataLine.trim().startsWith("HTTP/1")) {
      readlinesUntilBlankLineFound()
      val chunkSize = lineProcessor.readLine()
      logger.debug(s"chunk size be $chunkSize")
      var dataLine = lineProcessor.readLine().trim()
      logger.debug(dataLine)
      logger.debug(s"data line has length ${dataLine.length}")
      if((java.lang.Long.decode("0x" + chunkSize.trim()) - dataLine.length) == 1) {
        routeLine(LastResponseDataPart(requestNo, dataLine))
      } else {
        logger.debug(s"processing partial response for $requestNo : $dataLine")
        if(dataLine.matches(".+-+$")) {
          logger.debug("trim the trailing dashes...")
          dataLine = dataLine.replaceAll("-+$","")
          logger.debug(s"trimmed data line: $dataLine")
        }
        routeLine(ResponseDataPart(requestNo, dataLine))
      }
    } else {
      var trimmedDataLine = appDataLine.trim()
      logger.debug(s"processing $trimmedDataLine")
      if(trimmedDataLine.length == 0) {
        routeLine(LastResponseDataPart(requestNo, trimmedDataLine))
      } else {
        routeLine(ResponseDataPart(requestNo, trimmedDataLine))
      }
    }
  }

  def readlinesUntilBlankLineFound() {
    var line = "not blank"
    while(line.trim() != "") {
      line = lineProcessor.readLine()
    }
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

  def isDashLine(line: String) : Boolean = line.trim().startsWith("---------------")
}
