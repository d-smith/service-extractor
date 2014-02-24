package dumpreader

import java.sql.{PreparedStatement, DriverManager, Connection}
import org.slf4j.LoggerFactory

trait DBConnectInfo
case object NoDBConnection extends DBConnectInfo
case class OracleConnectInfo(user: String, password: String, url: String) extends DBConnectInfo

trait Persistor {
  def persist(timestamp: Double, serviceName: String, request: String, response: String)
}

class NoopPersistor extends Persistor {
  def persist(timestamp: Double, serviceName: String, request: String, response: String) {}
}

class OraclePersistor(connectionInfo: OracleConnectInfo) extends Persistor with Logging {
  logger.info("loading database driver")
  Class.forName("oracle.jdbc.OracleDriver");
  logger.info("driver loaded - connect to db")
  val connection: Connection =
    DriverManager.getConnection(connectionInfo.url,
      connectionInfo.user, connectionInfo.password)
  logger.info("connected to db - prepare statment")


  val insertSQL = "insert into service_call_dump (txn_timestamp, service_name, request, response) values (?,?,XMLType(?),XMLType(?))"
  val preparedStmt:PreparedStatement = connection.prepareStatement(insertSQL)
  logger.info("Statement prepared - ready to perist")

  def persist(timestamp: Double, serviceName: String, request: String, response: String) {
    try {
      preparedStmt.setDouble(1, timestamp)
      preparedStmt.setString(2, serviceName)

      val requestClob = connection.createClob()
      requestClob.setString(1, request)
      preparedStmt.setClob(3, requestClob)

      val responseClob = connection.createClob()
      responseClob.setString(1, response)
      preparedStmt.setClob(4, responseClob)

      preparedStmt.executeUpdate()
    } catch {
      case t:Throwable =>
        logger.warn(s"caught exception ${t.getMessage} persisting $timestamp, $serviceName")
        if(logger.isInfoEnabled()) logger.info(s"Persist error request and response: $request, $response")
    }

  }
}
