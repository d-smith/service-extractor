package dumpreader

import java.io.File
import org.slf4j.LoggerFactory
import akka.actor.{Props, ActorSystem}
import scala.concurrent.Await
import concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

object DumpProcessor extends App with Logging {
  try {

  val lineSeparator = System.getProperty("line.separator")




  var dbConnectInfo:DBConnectInfo = NoDBConnection

  if(args.length != 1 && args.length != 4) throw new Error("Need filename arg and optionally db user, password and url")
  if(args.length == 4) {
    dbConnectInfo = OracleConnectInfo(user = args(1), password = args(2), url = args(3))
  }

  implicit val timeout: Timeout = 2 seconds
  val system = ActorSystem()
  val lineRouter = system.actorOf(LineRouter.props(dbConnectInfo), "line-router")

  def shutdownHook() : Unit = {
    lineRouter ! PrintStats
    system.shutdown()
  }

  val lineProcessor = LineProcessor(new File(args(0)), shutdownHook)

  while(lineProcessor.moreLines()) {
    val line = lineProcessor.readLine
     processLine(line)
  }

  lineRouter ! PrintStats
  lineRouter ! Shutdown
  //system.shutdown() - need to defer shutdown until mailboxes are drained


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
    val future = (lineRouter ? HasRequest(requestNo)).mapTo[Boolean]
    val hasRequest = Await.result(future, 2 seconds)
    if(!hasRequest)
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
        lineRouter ! RequestData(requestNo, appDataLine)
        appDataLine = " " + lineProcessor.readLine().trim()
        if(appDataLine.contains("Envelope") && !envelope2) envelope2 = true
        else if(appDataLine.trim() =="" && envelope2) done = true
        else if(isDashLine(appDataLine)) done = true
      }
    }
  }

  def processResponse(requestNo: String) {
    var appDataLine = lineProcessor.readLine()
    if(appDataLine.trim().startsWith("HTTP/1")) {

      readlinesUntilBlankLineFound()
      val chunkSize = lineProcessor.readLine()
      logger.debug(s"chunk size be $chunkSize")

      //Read lines until the 0 line is found or until we find an application_data line or Handshake line
      var doneReadingResponseData = false
      var lines = List[String]()
      while(!doneReadingResponseData) {
        val line = lineProcessor.readLine().trim()
        if(responseDataEnds(line)) {
          var responseData = lines.foldLeft("") {
            (l, cl) => if(l == "") cl else l + lineSeparator + cl
          }

          responseData = removeTrailingDashes(requestNo, responseData)

         lineRouter ! ResponseDataPart(requestNo,responseData)
         lineProcessor.putReadLine(line)
         doneReadingResponseData = true

        } else if(line == "0") {

          var responseData = lines.foldLeft("") {
            (l, cl) => l + lineSeparator + cl
          }

          responseData = removeTrailingDashes(requestNo, responseData)

          lineRouter ! LastResponseDataPart(requestNo,responseData)
          doneReadingResponseData = true
        } else {
          lines = lines :+ line
        }
      }

    } else {
      if(appDataLine.length() > 4 && appDataLine.trim.equals("0")) {
        lineRouter ! LastResponseDataPart(requestNo, "")
      } else {
        var dataLine = appDataLine
        if(isChunkSizeLine(dataLine)) {
          dataLine = lineProcessor.readLine()
        }

        dataLine = removeTrailingDashes(requestNo, dataLine)
        lineRouter ! ResponseDataPart(requestNo, dataLine.trim())
      }

    }
  }

  def readlinesUntilBlankLineFound() {
    var line = "not blank"
    while(line.trim() != "") {
      line = lineProcessor.readLine()
    }
  }

  def doubleFromTimestamp(ts: String) : Double = {
    java.lang.Double.valueOf(ts)
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
    val timestamp = doubleFromTimestamp(connLine(2))
    lineRouter ! NewServiceCall(callNo, timestamp)
  }

  def applicationDataFollows(line: String) : Boolean = {
    if(line.trim().endsWith("application_data")) true else false
  }

  def responseDataEnds(s: String) : Boolean = {
    val trimmed = s.trim
    if(trimmed.endsWith("application_data") ||
      startsWithLineSpec(trimmed) ||
      trimmed.startsWith("New TCP connection")) true else false
  }

  def isDashLine(line: String) : Boolean = line.trim() == "---------------------------------------------------------------"

  def removeTrailingDashes(reqNo: String, s: String) : String = {
    var cleaned = s
    if(cleaned.matches(".+-+$")) {
      cleaned = cleaned.replaceAll("-+$","")
    }

    cleaned
  }


  def isChunkSizeLine(s: String) : Boolean = s.trim.matches("^[0-9A-Fa-f]+$")

  def startsWithLineSpec(line: String) : Boolean = {
    val splitLine = line.trim().split("\\s+")
    splitLine.length match {
      case x if x < 2 => false
      case _ => if(allDigits(splitLine(0)) && allDigits(splitLine(1))) true else false
    }
  }


  } catch {
    case t:Throwable =>
      t.printStackTrace()
      System.exit(1)
  }
}
