package dumpreader

import scala.xml.Node
import org.slf4j.LoggerFactory


sealed trait RouterEvent
case class NewServiceCall(requestNo: String, timeStamp: Long) extends RouterEvent
case class RequestData(requestNo: String, data: String) extends RouterEvent
case class ResponseDataPart(requestNo: String, data: String) extends RouterEvent
case class LastResponseDataPart(requestNo: String, data: String) extends RouterEvent

case class Transaction(timeStamp: Long, request: String = "", response: String = "") {
  def hasRequest() : Boolean = !request.equals("")
}

object LineRouter {
  val logger = LoggerFactory.getLogger(this.getClass)
  var txnMap = Map[String, Transaction]()
  var skippedMalformedResponse = 0
  var processedCount = 0


  def routeLine(event: RouterEvent) {
    event match {
      case NewServiceCall(reqNo, ts) =>
        logger.debug(s"New request $reqNo at $ts")
        txnMap += (reqNo -> Transaction(ts))
      case RequestData(reqNo, data) =>
        logger.debug(s"App data for request $reqNo: $data")
        txnMap.get(reqNo) match {
          case Some(txn) =>
            txnMap += (reqNo -> txn.copy(request = txn.request + data))
          case None =>
            txnMap += (reqNo -> Transaction(0, data, ""))
        }
      case ResponseDataPart(reqNo, data) =>
        txnMap.get(reqNo) match {
          case Some(txn) =>
            txnMap += (reqNo -> txn.copy(response = txn.response + data))
          case None =>
            txnMap += (reqNo -> Transaction(0,"", data))
        }
      case LastResponseDataPart(reqNo, data) =>
        txnMap.get(reqNo) match {
          case Some(txn) =>
            txnMap += (reqNo -> txn.copy(response = txn.response + data))
          case None =>
            txnMap += (reqNo -> Transaction(0,"", data))
        }
        dumpTxn(reqNo)
    }
  }

  def dumpTxn(reqNo: String) {
    val entry = txnMap.get(reqNo).get
    txnMap -= (reqNo)

    val request = trimGaps(entry.request.trim)
    val response = entry.response.trim
    val timestamp = entry.timeStamp

    logger.info(s"txn $reqNo has request $request and response $response")

    val service = extractServiceName(request)

    logger.debug(s"service is ${service}" )
    if(!isWellformed(response)) {
      logger.warn(s"Response is not well-formed for request $reqNo: $response")
      skippedMalformedResponse += 1
    } else {
      logger.info(s"request $reqNo: $timestamp $request $response")
      println(s"$reqNo|$timestamp|$service|$request|$response")
      processedCount += 1
    }
  }

  def extractServiceName(request: String) : String = {
    val xmlRep = xml.XML.loadString(request)
    val body: Node = (xmlRep \\ "Envelope" \ "Body") head
    val service = body.child.head.label
    service
  }

  def hasRequest(requestNo: String) : Boolean = {
    txnMap.get(requestNo) match {
      case Some(txn) => txn.hasRequest()
      case None => true
    }
  }

  def trimGaps(xml: String) : String = xml.replaceAll("> <", "><")

  def isWellformed(xmlString: String) : Boolean = {
    try {
      xml.XML.loadString(xmlString)
      true
    } catch {
      case _: Throwable => false
    }
  }

  def getMalformedCount() : Int = skippedMalformedResponse
  def getProcessedCount() : Int = processedCount
}
