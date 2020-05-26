package filters
import akka.stream.Materializer
import javax.inject.Inject
import play.api.mvc.{Filter, RequestHeader, Result}
import play.api.Logging
import play.api.http.HeaderNames

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class LoggingFilter @Inject()(implicit val mat: Materializer,
                              ec: ExecutionContext)
    extends Filter
    with Logging {
  def apply(
    nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime
      logger.info(
        s"${requestHeader.method} uri=${requestHeader.uri}  remote-address=${requestHeader.remoteAddress} took ${requestTime}ms and returned ${result.header.status}"
      )
      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}

class CorsFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext)
    extends Filter {
  def apply(
    nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      result.withHeaders(
        HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*",
        HeaderNames.ALLOW -> "*",
        HeaderNames.ACCESS_CONTROL_ALLOW_METHODS -> "POST, GET, PUT, DELETE, OPTIONS",
        HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS -> "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent"
      )
    }
  }
}
