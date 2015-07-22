package http

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingAdapter
import spray.routing._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import MediaTypes._

import responses.RequestHandler
import parsing.OmiParser
import PermissionCheck._
import types.{Path, OmiTypes}
import OmiTypes._
import database.DB

import scala.xml.NodeSeq
import scala.collection.JavaConversions.iterableAsScalaIterable

/**
 * Actor that handles incoming http messages
 * @param requestHandler ActorRef that is used in subscription handling
 */
class OmiServiceActor(reqHandler: RequestHandler) extends Actor with ActorLogging with OmiService {

  /**
   * the HttpService trait defines only one abstract member, which
   * connects the services environment to the enclosing actor or test
   */
  def actorRefFactory = context

  //Used for O-MI subscriptions
  val requestHandler = reqHandler

  /**
   * this actor only runs our route, but you could add
   * other things here, like request stream processing
   * or timeout handling
   */
  def receive = runRoute(myRoute)


}

/**
 * this trait defines our service behavior independently from the service actor
 */
trait OmiService extends HttpService with CORSSupport {
  import scala.concurrent.ExecutionContext.Implicits.global
  def log: LoggingAdapter
  val requestHandler: RequestHandler


  //Get the files from the html directory; http://localhost:8080/html/form.html
  val staticHtml = getFromDirectory("html")


  /** Some trickery to extract the _decoded_ uri path in current version of spray: */
  def pathToString: spray.http.Uri.Path => String = {
    case Uri.Path.Empty              => ""
    case Uri.Path.Slash(tail)        => "/"  + pathToString(tail)
    case Uri.Path.Segment(head, tail)=> head + pathToString(tail)
  }

  // should be removed?
  val helloWorld = get {
    respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default
      complete {
        <html>
        <body>
          <h1>Say hello to <i>O-MI Node service</i>!</h1>
          <ul>
            <li><a href="Objects">Url Data Discovery /Objects: Root of the hierarchy</a>
              <p>
                With url data discovery you can discover or request Objects,
                 InfoItems and values with HTTP Get request by giving some existing
                 path to the O-DF xml hierarchy.
              </p>
            </li>
            <li><a href="html/webclient/index.html">O-MI Test Client WebApp</a>
              <p>
                You can test O-MI requests here with the help of this webapp.
              </p>
            </li>
            <li style="color:gray;"><a style="text-decoration:line-through" href="html/old-webclient/form.html">Old WebApp</a>
              <p>
                Very old version of the webapp. Use this if the new doesn't work.
              </p>
            </li>
            <li><a href="html/ImplementationDetails.html">Implementation details, request-response examples</a>
              <p>
                Here you can view examples of the requests this project supports.
                These are tested against our server with <code>http.SystemTest</code>.
              </p>
            </li>
          </ul>
        </body>
        </html>
      }
    }
  }

  val getDataDiscovery =
    path(RestPath) { sprayPath =>
      get {
        // convert to our path type (we don't need very complicated functionality)
        val pathStr = pathToString(sprayPath)
        val path = Path(pathStr)

        requestHandler.generateODFREST(path) match {
          case Some(Left(value)) =>
            respondWithMediaType(`text/plain`) {
              complete(value)
            }
          case Some(Right(xmlData)) =>
            respondWithMediaType(`text/xml`) {
              complete(xmlData)
            }
          case None =>
            log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$path]")
            respondWithMediaType(`text/xml`) {
              complete((404, <error>No object found</error>))
            }
        }
      }
    }

  /* Receives HTTP-POST directed to root */
  val postXMLRequest = post { // Handle POST requests from the client
    clientIP { ip => // XXX: NOTE: This will fail if there isn't setting "remote-address-header = on"
      entity(as[NodeSeq]) { xml =>
        val eitherOmi = OmiParser.parse(xml.toString)
        //lazy val ip: RemoteAddress = ???

        respondWithMediaType(`text/xml`) {
          eitherOmi match {
            case Right(requests) =>
              val request = requests.head

              val (response, returnCode) = request match {

                case pRequest : PermissiveRequest => 
                  if(ip.toOption.exists(hasPermission(_))){//.nonEmpty && hasPermission(ip.toOption.get)) {
                    log.info(s"Authorized: ${ip.toOption} for ${pRequest.toString.take(80)}...")
                    requestHandler.handleRequest(pRequest)
                  } else {
                    log.warning(s"Unauthorized: ${ip.toOption} tried to use ${pRequest.toString.take(120)}...")
                    (requestHandler.unauthorized, 401)
                  }
                case req : OmiRequest => 
                    requestHandler.handleRequest(request)
              }

              complete((returnCode, response))

            case Left(errors) =>  // Errors found

              log.warning("Parse Errors: {}", errors.mkString(", "))

              val errorResponse = requestHandler.parseError(errors.toSeq:_*)

              complete((400, errorResponse))
          }
        }
      }
    }
  }

  // Combine all handlers
  val myRoute = cors {
    path("") {
      postXMLRequest ~
      helloWorld
    } ~
    pathPrefix("html") {
      staticHtml
    } ~
    pathPrefixTest("Objects") {
      getDataDiscovery
    }
  }
}
