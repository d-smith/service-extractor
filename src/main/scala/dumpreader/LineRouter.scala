package dumpreader

import scala.xml.Node
import org.slf4j.LoggerFactory
import akka.actor.Actor


sealed trait RouterEvent
case class NewServiceCall(requestNo: String, timeStamp: Double) extends RouterEvent
case class RequestData(requestNo: String, data: String) extends RouterEvent
case class ResponseDataPart(requestNo: String, data: String) extends RouterEvent
case class LastResponseDataPart(requestNo: String, data: String) extends RouterEvent

case class Transaction(timeStamp: Double, request: String = "", response: String = "") {
  def hasRequest() : Boolean = !request.equals("")
}

sealed trait RouterQuery
case class HasRequest(routeNo: String)

case object PersistTxns
case object PrintStats

class LineRouter extends Actor {
  import Persistor.persist

  val logger = LoggerFactory.getLogger(this.getClass)
  var txnMap = Map[String, Transaction]()
  var skipped = 0
  var processedCount = 0
  var persistTxns = false

  //val txnDumper = context.actorOf(Props[])


  def receive = {
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

      case PersistTxns => persistTxns = true
      case PrintStats => println(s"skipped ${getMalformedCount()} processed ${getProcessedCount()}")

      case HasRequest(reqNo) => sender ! hasRequest(reqNo)
  }

  private def dumpTxn(reqNo: String) {
    val entry = txnMap.get(reqNo).get
    txnMap -= (reqNo)

    val request = trimGaps(entry.request.trim)
    var response = entry.response.trim
    val timestamp = entry.timeStamp

    logger.info(s"txn $reqNo has request $request and response $response")
    if(request.trim() == "") {
      logger.debug(s"skipping $reqNo - no request")
      skipped += 1
    } else {

      val service = extractServiceName(request)

      logger.debug(s"service is ${service}" )
      if(!isWellformed(response)) {
        logger.warn(s"Response is not well-formed for request $reqNo: $response")
        skipped += 1
        response = "<truncated/>"
      }

      logger.info(s"request $reqNo: $timestamp $request $response")
      println(s"$reqNo|$timestamp|$service|$request|$response")
      processedCount += 1
      if(persistTxns) {
        persist(timestamp, service, request, response)
      }

    }
  }

  private def extractServiceName(request: String) : String = {
    val xmlRep = xml.XML.loadString(request)
    val body: Node = (xmlRep \\ "Envelope" \ "Body") head
    val service = body.child.head.label
    service
  }

  private def hasRequest(requestNo: String) : Boolean = {
    txnMap.get(requestNo) match {
      case Some(txn) => txn.hasRequest()
      case None => true
    }
  }

  private def trimGaps(xml: String) : String = xml.replaceAll("> <", "><")

  private def isWellformed(xmlString: String) : Boolean = {
    try {
      xml.XML.loadString(xmlString)
      true
    } catch {
      case _: Throwable => false
    }
  }

  private def getMalformedCount() : Int = skipped
  private def getProcessedCount() : Int = processedCount


}

case class TxnSpec(persist: Boolean, ts: Double, serviceName: String, request: String, response: String)

class TransactionDumper extends Actor {
  def receive = {
    case TxnSpec(persist, ts, serviceName, request, response) =>


  }
}
