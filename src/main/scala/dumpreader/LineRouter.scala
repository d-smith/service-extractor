package dumpreader


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
      case _ => println("Handler implementation not complete")
    }
  }

  def hasRequest(requestNo: String) : Boolean = {
    txnMap.get(requestNo) match {
      case Some(txn) => txn.hasRequest()
      case None => true
    }
  }


}
