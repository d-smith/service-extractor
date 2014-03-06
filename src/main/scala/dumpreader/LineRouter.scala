package dumpreader

import scala.xml.Node
import org.slf4j.LoggerFactory
import akka.actor.{PoisonPill, ActorRef, Props, Actor}
import scala.Some
import akka.routing.FromConfig
import akka.dispatch.sysmsg.Watch
import dumpreader.Reaper.WatchMe


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

case object PrintStats
case object Shutdown

object LineRouter {
  def props(dbConnectInfo: DBConnectInfo) : Props =  Props(classOf[LineRouter], dbConnectInfo)
}

class LineRouter(dbConnectInfo: DBConnectInfo) extends Actor with Logging {

  var txnMap = Map[String, Transaction]()
  var skipped = 0
  var processedCount = 0
  val reaper = context.actorOf(Props[AllPersistedReaper])
  val txnDumper = context.actorOf(TransactionDumper.props(dbConnectInfo,reaper)
                      .withRouter(FromConfig()), "dump-router")


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

      case PrintStats => logger.warn(s"skipped ${getMalformedCount()} processed ${getProcessedCount()}")

      case HasRequest(reqNo) => sender ! hasRequest(reqNo)

      case Shutdown => shutdown()
  }

  private def shutdown() {
    logger.warn(s"dumping ${txnMap.size} partial transactions")
    txnMap.keys.foreach {
      k => dumpTxn(k)
    }
    context.system.actorSelection("/user/line-router/dump-router/*") ! PoisonPill
  }

  private def dumpTxn(reqNo: String) {
    val entry = txnMap.get(reqNo).get
    txnMap -= (reqNo)

    val request = trimGaps(entry.request.trim)
    var response = entry.response.trim
    val timestamp = entry.timeStamp

    if(request.trim() == "") {
      logger.debug(s"skipping $reqNo - no request")
      skipped += 1
    } else {

      val service = extractServiceName(request)

      logger.debug(s"service is ${service}" )
      if(!isWellformed(response)) {
        logger.warn(s"Response is not well-formed for request $reqNo")
        if(logger.isInfoEnabled()) logger.info(s"Response for $reqNo is $response")
        response = "<truncated/>"
      }


      txnDumper ! TxnSpec(reqNo, timestamp, service, request, response)
      processedCount += 1

    }
  }

  private def extractServiceName(request: String) : String = {
    try {
      val xmlRep = xml.XML.loadString(request)
      val body: Node = (xmlRep \\ "Envelope" \ "Body") head
      val service = body.child.head.label
      service
    } catch {
      case t:Throwable =>
        logger.warn(s"Unable to extract service name - problem: ${t.getMessage} - request $request")
        if(logger.isInfoEnabled()) logger.info(s"problem request is $request")
        "UnknownService"
    }
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
      case t: Throwable =>
        logger.warn(s"Problem parsing response: ${t.getMessage}")
        false
    }
  }

  private def getMalformedCount() : Int = skipped
  private def getProcessedCount() : Int = processedCount


}

case class TxnSpec(reqNo: String, ts: Double, serviceName: String, request: String, response: String)

object TransactionDumper {
  def props(dbConnectInfo: DBConnectInfo, reaper: ActorRef) : Props =  Props(classOf[TransactionDumper], dbConnectInfo, reaper)
}

class TransactionDumper(dbConnectInfo: DBConnectInfo, reaper: ActorRef) extends Actor with Logging {
  val persistor: Persistor = dbConnectInfo match {
    case oraConnectInfo: OracleConnectInfo => new OraclePersistor(oraConnectInfo)
    case _ => new NoopPersistor
  }

  logger.info("Register with reaper")
  reaper ! WatchMe(self)


  def receive = {
    case TxnSpec(reqNo, timestamp, serviceName, request, response) =>
      logger.debug(s"request $reqNo: $timestamp $request $response")
      persistor.persist(timestamp, serviceName, request, response)

  }
}
