package dumpreader

import java.sql.{PreparedStatement, DriverManager, Connection}
import org.slf4j.LoggerFactory

object Persistor {
  var connection: Connection = _
  val insertSQL = "insert into service_call_dump (txn_timestamp, service_name, request, response) values (?,?,XMLType(?),XMLType(?))"
  val logger = LoggerFactory.getLogger(this.getClass)
  var preparedStmt:PreparedStatement = _

  def setDBCredentials(user: String, password: String, url: String) {
    connection = DriverManager.getConnection(url, user, password)
  }

  def persist(timestamp: Double, serviceName: String, request: String, response: String) {
    try {
      val preparedStmt = connection.prepareStatement(insertSQL)
      preparedStmt.setDouble(1, timestamp)
      preparedStmt.setString(2, serviceName)

      val requestClob = connection.createClob()
      requestClob.setString(1, request)
      preparedStmt.setClob(3, requestClob)

      val responseClob = connection.createClob()
      responseClob.setString(1, response)
      preparedStmt.setClob(4, responseClob)

      preparedStmt.executeUpdate()
      preparedStmt.close()
    } catch {
      case t:Throwable =>
        logger.warn(s"caught exception ${t.getMessage} for $timestamp, $serviceName, $request, $response")
    }

  }
}
