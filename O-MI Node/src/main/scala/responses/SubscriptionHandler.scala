package responses

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging

import database._
import types.Path
import CallbackHandlers._
import types.OmiTypes._
import types.OdfTypes._
import parsing.xmlGen
import parsing.xmlGen.scalaxb

import scala.collection.mutable.PriorityQueue

import java.sql.Timestamp
import java.util.Date
import System.currentTimeMillis
import scala.math.Ordering
import scala.util.{Try, Success, Failure }
import scala.collection.mutable.{ Map, HashMap }

import xml._
import scala.concurrent.duration._
import scala.concurrent._
import scala.collection.JavaConversions.iterableAsScalaIterable

// MESSAGES
case object HandleIntervals
case object CheckTTL
case class RegisterRequestHandler(reqHandler: RequestHandler)
case class NewSubscription(subscription: SubscriptionRequest)
case class RemoveSubscription(id: Int)

/**
 * Handles interval counting and event checking for subscriptions
 */
class SubscriptionHandler(implicit dbConnection : DB ) extends Actor with ActorLogging {
  import ExecutionContext.Implicits.global
  import context.system

  private def date = new Date()
  implicit val timeout = Timeout(5.seconds)

  private var requestHandler = new RequestHandler(self)

  sealed trait SavedSub {
    val sub: DBSub
    val id: Int
  }

  case class TimedSub(sub: DBSub, nextRunTime: Timestamp)
    extends SavedSub {
      val id = sub.id
  }

  case class EventSub(sub: DBSub, lastValue: String)
    extends SavedSub {
      val id = sub.id
  }

  object TimedSubOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) =
      a.nextRunTime.getTime compare b.nextRunTime.getTime
  }

  private var intervalSubs: PriorityQueue[TimedSub] = {
    PriorityQueue()(TimedSubOrdering.reverse)
  }
  def getIntervalSubs = intervalSubs

  //var eventSubs: Map[Path, EventSub] = HashMap()
  private var eventSubs: Map[String, Seq[EventSub]] = HashMap()
  def getEventSubs = eventSubs

  // Attach to db events
  database.attachSetHook(this.checkEventSubs _)

  // load subscriptions at startup
  override def preStart() = {
    val subs = dbConnection.getAllSubs(Some(true))
    for (sub <- subs) loadSub(sub)

  }

  private def loadSub(id: Int): Unit = {
    dbConnection.getSub(id) match {
      case Some(dbsub) =>
        loadSub(dbsub)
      case None =>
        log.error(s"Tried to load nonexistent subscription: $id")
    }
  }
  private def loadSub(dbsub: DBSub): Unit = {
    log.debug(s"Adding sub: $dbsub")

    if (dbsub.hasCallback){
      if (dbsub.isIntervalBased) {
        intervalSubs += TimedSub(
          dbsub,
          new Timestamp(currentTimeMillis())
        )

        // FIXME: schedules many times
        handleIntervals()

        log.debug(s"Added sub as TimedSub: $dbsub")

      } else if (dbsub.isEventBased) {

        dbConnection.getSubscribedItems(dbsub.id) foreach {
          case SubscriptionItem(_, path, lastValueO) =>
            val lastValue: String = lastValueO match {
              case Some(v) => v
              case None    => (for {
                infoItem <- dbConnection.get(path)
                last     <- infoItem match {
                  case info: OdfInfoItem => info.values.headOption
                  case o => //noop
                    log.error(s"Didn't expect other than InfoItem: $o")
                    None
                }
              } yield last.value).getOrElse("")
            }
           eventSubs.get(path.toString) match {

              case Some( ses : Seq[EventSub] ) => 
                eventSubs = eventSubs.updated(
                  path.toString, EventSub(dbsub, lastValue) :: ses.toList
                )

              case None => 
                eventSubs += path.toString -> Seq( EventSub(dbsub, lastValue))
            }
        }

        log.debug(s"Added sub as EventSub: $dbsub")
      }
    } else if (! dbsub.isImmortal ) {
      log.debug(s"Added sub as polled sub: $dbsub")
      ttlQueue.enqueue((dbsub.id, (dbsub.ttlToMillis + dbsub.startTime.getTime)))
      self ! CheckTTL
    }
  }

  /**
   * @param id The id of subscription to remove
   * @return true on success
   */
  private def removeSub(id: Int): Boolean = {
    log.debug(s"Removing sub $id...")
    dbConnection.getSub(id) match {
      case Some(dbsub) => removeSub(dbsub)
      case None => false
    }
  }
  
  /**
   * @param sub The subscription to remove
   * @return true on success
   */
  private def removeSub(sub: DBSub): Boolean = {
    if (sub.isEventBased) {
      val removedPaths = dbConnection.getSubscribedPaths(sub.id) 
      removedPaths foreach { removedPath => 
        val path = removedPath.toString
        eventSubs get path match {
          case Some(ses: Seq[EventSub]) if ses.length > 1 =>
            eventSubs.updated(path, ses.filter( _.sub.id != sub.id ))    
          case Some(ses: Seq[EventSub]) if ses.length == 1 &&
              ses.headOption.map(_.sub.id == sub.id).getOrElse(false) =>
            // remove the whole entry to keep empty lists from piling up in the map
            eventSubs -= path
          case None => // noop
        } 
      }
    } else {
      //remove from intervalSubs
      if(sub.hasCallback)
        intervalSubs = intervalSubs.filterNot(sub.id == _.id)
      else
        ttlQueue = ttlQueue.filterNot( sub.id == _._1)
    }
    dbConnection.removeSub(sub.id)
  }

  override def receive = {

    case HandleIntervals => handleIntervals()

    case CheckTTL => checkTTL()

    case NewSubscription(subscription) => sender() ! setSubscription(subscription)

    case RemoveSubscription(requestID) => sender() ! removeSub(requestID)
  
    case RegisterRequestHandler(reqHandler: RequestHandler) => requestHandler = reqHandler
  }

  /**
   * @param paths Paths of modified InfoItems.
   */
  def checkEventSubs(items: Seq[OdfInfoItem]): Unit = {
    val idItemLastVal = items.flatMap{ item =>
      val itemPath = item.path
      val subItemTuples  = eventSubs.collect{
        case ( path, subs) if itemPath.toString  == path.toString => 
        subs
      }.flatten

      subItemTuples.map{ eventsub => (eventsub.sub.id, item, eventsub.lastValue ) }
    }

    val idToItems =idItemLastVal.groupBy{ 
      case (id, item, lastValue ) =>
      id 
    }.mapValues{ _.collect{
      case (id,item, lastValue) if item.values.dropWhile(_ == lastValue).nonEmpty => 
        val newVals = item.values.dropWhile(_ == lastValue)
        eventSubs = eventSubs.updated(
          item.path.toString,
          eventSubs.get(item.path.toString).get.map{
            esub => EventSub(esub.sub, newVals.last.value.toString)
          }
        )
        fromPath( OdfInfoItem( item.path, collection.JavaConversions.asJavaIterable(newVals) ) ) 

      }.foldLeft(OdfObjects())(_ combine _) 
    }
    idToItems.foreach{ case (id, odf) =>
        log.debug(s"Sending data to event sub: $id.")
        val subs =  eventSubs.values.flatten.filter(_.sub.id == id )
        if( subs.isEmpty) 
         return;

        val callbackAddr = subs.head.sub.callback.get
        val xmlMsg = requestHandler.xmlFromResults(
              1.0,
              Result.pollResult( id.toString, odf )
            )
        log.info(s"Sending in progress; Subscription subId:${id} addr:$callbackAddr interval:-1")

        def failed(reason: String) =
          log.warning(
            s"Callback failed; subscription id:${id} interval:-1  reason: $reason")


        sendCallback(callbackAddr, xmlMsg) onComplete {
            case Success(CallbackSuccess) =>
              log.info(s"Callback sent; subscription id:${id} addr:$callbackAddr interval:-1")

            case Success(fail: CallbackFailure) =>
              failed(fail.toString)
            case Failure(e) =>
              failed(e.getMessage)
          }
    }
    /*
    for (infoItem <- items; path <- infoItem.path.getParentsAndSelf) {
>>>>>>> Stashed changes
      var newestValue: Option[String] = None

      eventSubs.get(path.toString) match {

        case Some(ses : Seq[EventSub]) => {
          ses.foreach{
            case e@EventSub(subscription, lastValue) => 
              if (hasTTLEnded(subscription, currentTimeMillis())) {
                removeSub(subscription)
              } else {
                if (newestValue.isEmpty)
                  newestValue = dbConnection.get(path).map{
                    case OdfInfoItem(_, v, _, _) => iterableAsScalaIterable(v).headOption.map{
                      case value : OdfValue =>
                        value.value
                    }.getOrElse("")
                    case _ => ""// noop, already logged at loadSub
                  }
               
                
                if (lastValue != newestValue.getOrElse("")){

                  //def failed(reason: String) =
                  //  log.warning(s"Callback failed; subscription id:$id  reason: $reason")

                  val addr = subscription.callback 
                  if (addr == None) return

                  val xmlMsg = requestHandler.handleRequest(SubDataRequest(subscription))

                }
              }
          }
        }

        case None => // noop
      }
    }*/
  }

  private def hasTTLEnded(sub: DBSub, timeMillis: Long): Boolean = {
    val removeTime = sub.startTime.getTime + sub.ttlToMillis

    if (removeTime <= timeMillis && !sub.isImmortal) {
      log.debug(s"TTL ended for sub: id:${sub.id} ttl:${sub.ttlToMillis} delay:${timeMillis - removeTime}ms")
      true
    } else
      false
  }

  def handleIntervals(): Unit = {
    // failsafe
    if (intervalSubs.isEmpty) {
      log.error("handleIntervals shouldn't be called when there is no intervalSubs!")
      return
    }

    val checkTime = currentTimeMillis()

    while (intervalSubs.headOption.exists(_.nextRunTime.getTime <= checkTime)) {

      val TimedSub(sub, time) = intervalSubs.dequeue()

      log.debug(s"handleIntervals: delay:${checkTime - time.getTime}ms currentTime:$checkTime targetTime:${time.getTime} id:${sub.id}")

      // Check if ttl has ended, comparing to original check time
      if (hasTTLEnded(sub, checkTime)) {

        dbConnection.removeSub(sub.id)

      } else {
        val numOfCalls = ((checkTime - sub.startTime.getTime) / sub.intervalToMillis).toInt

        val newTime = new Timestamp(sub.startTime.getTime.toLong + sub.intervalToMillis * (numOfCalls + 1))
        //val newTime = new Timestamp(time.getTime + sub.intervalToMillis) // OLD VERSION

        intervalSubs += TimedSub(sub, newTime)


        log.debug(s"generateOmi for subId:${sub.id}")
        requestHandler.handleRequest(SubDataRequest(sub))//Returns tuple, second is return status
      }
    }

    // Schedule for next
    intervalSubs.headOption foreach { next =>

      val nextRun = next.nextRunTime.getTime - currentTimeMillis()
      system.scheduler.scheduleOnce(nextRun.milliseconds, self, HandleIntervals)

      log.debug(s"Next subcription handling scheluded after ${nextRun}ms")
    }
  }


  /**
   * typedef for (Int,Long) tuple where values are (subID,ttlInMilliseconds + startTime).
   */
  type SubTuple = (Int, Long)

  /**
   * define ordering for priorityQueue this needs to be reversed when used, so that sub with earliest timeout is first.
   */
  val subOrder: Ordering[SubTuple] = Ordering.by(_._2)

  /**
   * PriorityQueue with subOrder ordering. value with earliest timeout is first.
   * This val is lazy and is computed when needed for the first time
   *
   * This queue contains only subs that have no callback address defined and have ttl > 0.
   */
  private var ttlQueue: PriorityQueue[SubTuple] = new PriorityQueue()(subOrder.reverse)
  var scheduledTimes: Option[(akka.actor.Cancellable, Long)] = None
  
  /**
   * @return Either Failure(exception) or the request (subscription) id as Success(Int)
   */
  def setSubscription(subscription: SubscriptionRequest)(implicit dbConnection: DB) : Try[Int] = Try {
    require(subscription.ttl > 0.seconds, "Zero time-to-live not supported")

    val paths = getPaths(subscription)

    require(paths.nonEmpty, "Subscription request should have some items to subscribe.")

    val interval = subscription.interval
    val ttl      = subscription.ttl
    val callback = subscription.callback
    val timeStamp = new Timestamp(date.getTime())

    val newSub = NewDBSub(interval, timeStamp, ttl, callback)
    val dbsub = dbConnection.saveSub( newSub, paths )

    val requestID = dbsub.id 
    Future{
      loadSub(dbsub)
      } onComplete {
        case Success(v) => println("SUCCESS")
        case Failure(e) => log.error(e.getStackTrace().mkString("\n"))
      }
    requestID
  }

  def getPaths(request: OdfRequest) = getLeafs(request.odf).map{ _.path }.toSeq

  
  /**
   * This method is called by scheduler and when new sub is added to subQueue.
   *
   * This method removes all subscriptions that have expired from the priority queue and
   * schedules new checkSub method call.
   */
  def checkTTL()(implicit dbConnection: DB): Unit = {
    
    val currentTime = date.getTime
    
    //exists returns false if Option is None
    while (ttlQueue.headOption.exists(_._2 <= currentTime)) {
      dbConnection.removeSub(ttlQueue.dequeue()._1)
    }
    
    //foreach does nothing if Option is None
    ttlQueue.headOption.foreach { n =>
      val nextRun = ((n._2) - currentTime)
      val cancellable = system.scheduler.scheduleOnce(nextRun.milliseconds, self, CheckTTL)
      if (scheduledTimes.forall(_._1.isCancelled)) {
        scheduledTimes = Some((cancellable, currentTime + nextRun))

      } else if (scheduledTimes.exists(_._2 > (currentTime + nextRun))) {
        scheduledTimes.foreach(_._1.cancel())
        scheduledTimes=Some((cancellable,currentTime+nextRun))
      }
      else if (scheduledTimes.exists(n =>((n._2 > currentTime) && (n._2 < (currentTime + nextRun))))) {
        cancellable.cancel()
      }
    }
  }
}
