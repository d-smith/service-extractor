package dumpreader

import scala.xml.Node


sealed trait RouterEvent
case class NewServiceCall(requestNo: String, timeStamp: Long) extends RouterEvent
case class RequestData(requestNo: String, data: String) extends RouterEvent
case class ResponseDataPart(requestNo: String, data: String) extends RouterEvent
case class LastResponseDataPart(requestNo: String, data: String) extends RouterEvent

case class Transaction(timeStamp: Long, request: String = "", response: String = "") {
  def hasRequest() : Boolean = !request.equals("")
}

object LineRouter {

  var txnMap = Map[String, Transaction]()

  def routeLine(event: RouterEvent) {
    event match {
      case NewServiceCall(reqNo, ts) =>
        println(s"New request $reqNo at $ts")
        txnMap += (reqNo -> Transaction(ts))
      case RequestData(reqNo, data) =>
        println(s"App data for request $reqNo: $data")
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
    println(s"txn $reqNo has request ${trimGaps(entry.request)} and response ${entry.response}")

    val xmlRep = xml.XML.loadString(trimGaps(entry.request))
    val body: Node = (xmlRep \\ "Envelope" \ "Body") head
    val service = body.child.head.label

    println(s"service is ${service}" )
  }

  def hasRequest(requestNo: String) : Boolean = {
    txnMap.get(requestNo) match {
      case Some(txn) => txn.hasRequest()
      case None => true
    }
  }

  def trimGaps(xml: String) : String = xml.replaceAll("> <", "><")


}
