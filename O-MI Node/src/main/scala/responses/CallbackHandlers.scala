package responses

import scala.concurrent._

import akka.actor.ActorSystem
import spray.http._
import spray.client.pipelining._



// Errors
sealed trait CallbackResult
sealed class CallbackFailure               extends CallbackResult

case object  CallbackSuccess               extends CallbackResult
case class   HttpError(status: StatusCode) extends CallbackFailure
case object  ProtocolNotSupported          extends CallbackFailure



object CallbackHandlers {
  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures

  val httpHandler: HttpRequest => Future[HttpResponse] = sendReceive

  def sendCallback(address: Uri, data: xml.NodeSeq): Future[CallbackResult] = {

    address.scheme match {

      case "http" =>

        val request = Post(address, data)
        val responseFuture = httpHandler(request)
        
        responseFuture map { response =>

          if (response.status.isSuccess)
            CallbackSuccess
          else
            HttpError(response.status)
        }

      case _ =>
        Future{ ProtocolNotSupported }

    }
  }

}
