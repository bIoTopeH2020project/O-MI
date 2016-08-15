/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package agentSystem

import scala.reflect.ClassTag
import scala.util.{Success, Failure, Try}
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }
import akka.actor.{
  Actor,
  ActorLogging,
  Props,
  ActorInitializationException
}
import akka.actor.Actor.Receive
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import types.OdfTypes._
import types.OmiTypes._
import types.Path
/**
 Commands that can be received from InternalAgentLoader.
 **/
sealed trait InternalAgentCmd
case class Start()                    extends InternalAgentCmd
case class Restart()                  extends InternalAgentCmd
case class Stop()                     extends InternalAgentCmd

trait InternalAgentResponse
trait InternalAgentSuccess     extends InternalAgentResponse 
case class CommandSuccessful() extends InternalAgentSuccess 

class InternalAgentFailure(msg : String , exp : Throwable )  extends  Exception(msg, exp) with InternalAgentResponse
case class CommandFailed(msg : String ) extends InternalAgentFailure(msg, null) 
case class StartFailed(msg : String, exp : Throwable ) extends InternalAgentFailure(msg, exp) 

sealed trait ResponsibleAgentMsg
case class ResponsibleWrite( promise: Promise[ResponsibleAgentResponse], write: WriteRequest)

sealed trait ResponsibleAgentResponse{
  def combine( other: ResponsibleAgentResponse ) : ResponsibleAgentResponse 
}
case class SuccessfulWrite( paths: Vector[Path] ) extends ResponsibleAgentResponse{
  def combine( other: ResponsibleAgentResponse ) : ResponsibleAgentResponse = {
    other match{
      case SuccessfulWrite( opaths ) => SuccessfulWrite( paths ++ opaths)
      case fw @ FailedWrite( opaths, reason ) => MixedWrite( paths, fw )
      case MixedWrite( successed, failed ) => MixedWrite( paths ++ successed, failed )
    }
  }
} 
case class FailedWrite( paths: Vector[Path], reasons: Vector[Throwable] ) extends ResponsibleAgentResponse {
  def combine( other: ResponsibleAgentResponse ) : ResponsibleAgentResponse = {
    other match{
      case SuccessfulWrite( opaths ) => MixedWrite(opaths, this)
      case fw @ FailedWrite( opaths, oreasons ) => FailedWrite( paths ++ opaths, reasons ++ oreasons )
      case MixedWrite( successed, failed ) => MixedWrite( successed, FailedWrite( paths ++ failed.paths, reasons ++ failed.reasons) )
    }
  }
} 
case class MixedWrite( successed: Vector[Path], failed: FailedWrite ) extends ResponsibleAgentResponse{
  def combine( other: ResponsibleAgentResponse ) : ResponsibleAgentResponse = {
    other match{
      case SuccessfulWrite( opaths ) => MixedWrite( successed ++ opaths, failed)
      case fw @ FailedWrite( opaths, reason ) => MixedWrite( successed , FailedWrite( failed.paths ++ fw.paths, failed.reasons ++ fw.reasons))
      case MixedWrite( osuccessed, ofailed ) => MixedWrite( successed ++ osuccessed, FailedWrite( failed.paths ++ ofailed.paths, failed.reasons ++ ofailed.reasons) )
    }
  }
}  


trait ScalaInternalAgent extends InternalAgent with ActorLogging{
  def config : Config
  def parent = context.parent
  def agentSystem = context.parent
  def name = self.path.name
  def restart : InternalAgentSuccess = {
    stop 
    start
  }
  //These need to be implemented 
  def start   : InternalAgentSuccess 
  def stop    : InternalAgentSuccess 
  def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart
    case Stop() => sender() ! stop

   }

}

trait ResponsibleInternalAgent extends ScalaInternalAgent {
  import context.dispatcher
  protected def handleWrite( write: WriteRequest ) :Unit

  final protected def passWrite(write: WriteRequest) = {
    implicit val timeout = Timeout( write.handleTTL)

    val senderRef = sender()
    val future = (agentSystem ? write).mapTo[ResponsibleAgentResponse]
    future.onComplete{
      case Success( result ) => senderRef ! result 
      case Failure( t ) => senderRef ! FailedWrite(write.odf.paths, Vector(t))
    }

  }
  final protected def incorrectWrite(write: WriteRequest) = {
    sender() ! FailedWrite(write.odf.paths, Vector(new Exception(s"Write incorrect. Tryed to write incorrect value.")))
  }
  final protected def forbiddenWrite(write: WriteRequest) = {
    sender() ! FailedWrite(write.odf.paths, Vector(new Exception(s"Write forbidden. Tryed to write to path that is not mean to be writen.")))
  }
}

