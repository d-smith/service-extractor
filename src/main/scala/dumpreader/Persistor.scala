package dumpreader

import java.sql.{DriverManager, Connection}
import org.slf4j.LoggerFactory

object Persistor {
  var connection: Connection = _
  val insertSQL = "insert into service_call_dump (txn_timestamp, service_name, request, response) values (?,?,?,?)"
  val logger = LoggerFactory.getLogger(this.getClass)

  def setDBCredentials(user: String, password: String, url: String) {
    connection = DriverManager.getConnection(url, user, password)
  }

  def persist(timestamp: Long, serviceName: String, request: String, response: String) {
    try {
      val preparedStmt = connection.prepareStatement(insertSQL)
      preparedStmt.setLong(1, timestamp)
      preparedStmt.setString(2, serviceName)
      preparedStmt.setString(3, request)
      preparedStmt.setString(4, request)
      preparedStmt.executeUpdate()
      preparedStmt.close()
    } catch {
      case t:Throwable =>
        logger.warn(s"caught exception ${t.getMessage} for $timestamp, $serviceName, $request, $response")
    }

  }
}
