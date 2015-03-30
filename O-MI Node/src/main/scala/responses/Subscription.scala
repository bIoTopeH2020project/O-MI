package responses

import Common._
import parsing.Types._
import parsing.Types.Path._
import database._
import scala.xml
import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import java.sql.Timestamp
import java.util.Date

object OMISubscription {
  /**
   * Creates a subscription in the database and generates the immediate answer to a subscription
   *
   * @param subscription an object of Subscription class which contains information about the request
   * @return A tuple with the first element containing the requestId and the second element
   * containing the immediate xml that's used for responding to a subscription request
   */

  def setSubscription(subscription: Subscription): (Int, xml.NodeSeq) = {
    var requestIdInt: Int = -1
    val paths = getInfoItemPaths(subscription.sensors.toList)

    if (paths.isEmpty == false) {
      val xml =
        omiResult {
          returnCode200 ++
            requestId {

              val ttlInt = subscription.ttl.toInt
              val interval = subscription.interval.toInt
              val callback = subscription.callback

              requestIdInt = SQLite.saveSub(
                new DBSub(paths.toArray, ttlInt, interval, callback, Some(new Timestamp(new Date().getTime()))))

              requestIdInt
            }
        }

      return (requestIdInt, xml)
    } else {
      val xml =
        omiResult {
          returnCode(400, "No InfoItems found in the paths")
        }

      return (requestIdInt, xml)
    }
  }

  /**
   * Used for getting only the infoitems from the request. If an Object is subscribed to, get all the infoitems
   * that are children (or children's children etc.) of that object.
   *
   * @param A hierarchy of the ODF-structure that the parser creates
   * @return Paths of the infoitems
   */

  def getInfoItemPaths(objects: List[OdfObject]): Buffer[Path] = {
    var paths = Buffer[Path]()
    for (obj <- objects) {
/*      //just an object has been subscribed to
      if (obj.childs.nonEmpty == false && obj.sensors.nonEmpty == false) {

      }*/

      if (obj.childs.nonEmpty) {
        paths ++= getInfoItemPaths(obj.childs.toList)
      }

      if (obj.sensors.nonEmpty) {
        for (sensor <- obj.sensors) {
          SQLite.get(sensor.path) match {
            case Some(infoitem: DBSensor) => paths += infoitem.path
            case _ => //do nothing
          }
        }
      }
    }
    return paths
  }


/*  def getObjectChildren(object: DBObject) {
    
  }*/

  /**
   * Subscription response
   *
   * @param Id of the subscription
   * @return The response XML
   */

  def OMISubscriptionResponse(id: Int): xml.NodeSeq = {
    SQLite.getSub(id) match {
      case None => {
        omiResult {
          returnCode(400, "A subscription with this id has expired or doesn't exist") ++
            requestId(id)
        }
      }

      case _ => {
        omiResult {
          returnCode200 ++
            requestId(id) ++
            odfMsgWrapper(odfGeneration(id))
        }
      }
    }
  }

  /**
   * Used for generating data in ODF-format. When the subscription has callback set it acts like a OneTimeRead with a requestID,
   * when it doesn't have a callback it generates the values accumulated in the database.
   *
   * @param Id of the subscription
   * @return The data in ODF-format
   */

  def odfGeneration(id: Int): xml.NodeSeq = {
    val subdata = SQLite.getSub(id).get
    subdata.callback match {
      case Some(callback: String) => {
        <Objects>
          { createFromPaths(subdata.paths, 1, subdata.startTime, subdata.interval, true) }
        </Objects>
      }
      case None => { //subscription polling
        subdata.interval match {
          case -2 => {
            <Error> Interval not supported </Error>
          }; // not supported
          case -1 => { //Event based subscription
            val start = subdata.startTime.getTime
            val currentTimeLong = new Date().getTime()
            val newTTL: Double = {
              if (subdata.ttl <= 0) subdata.ttl
              else ((subdata.ttl * 1000).toLong - (currentTimeLong - start)) / 1000.0
            }
            
            SQLite.setSubStartTime(subdata.id,new Timestamp(currentTimeLong), newTTL)

            <Objects>
              { createFromPaths(subdata.paths, 1, subdata.startTime, subdata.interval, false) }
            </Objects>
          }; //eventsub
          case 0 => {
            <Error> Interval not supported </Error>
          }; //not supported
          case _ => { //Interval based subscription
            val start = subdata.startTime.getTime
            val currentTimeLong = new Date().getTime()
            //calculate new start time to be divisible by interval to keep the scheduling
            //also reduce ttl by the amount that startTime was changed
            val intervalMillisLong = (subdata.interval * 1000).toLong
            val newStartTimeLong = start + (intervalMillisLong * ((currentTimeLong - start) / intervalMillisLong)) //subdata.startTime.getTime + ((intervalMillisLong) * ((currentTimeLong - subdata.startTime.getTime) / intervalMillisLong).toLong)
            val newTTL: Double = if (subdata.ttl <= 0.0) subdata.ttl else { //-1 and 0 have special meanings
              ((subdata.ttl * 1000).toLong - (newStartTimeLong - start)) / 1000.0
            }

            SQLite.setSubStartTime(subdata.id, new Timestamp(newStartTimeLong), newTTL)

            <Objects>
              { createFromPaths(subdata.paths, 1, subdata.startTime, subdata.interval, false) }
            </Objects>
          }
        }
      }
    }
  }

  /**
   * Uses the Dataformater from database package to get a list of the values that have been accumulated during the start of the sub and the request
   *
   * @param The InfoItem that's been subscribed to
   * @param Start time of the subscription
   * @param Interval of the subscription
   * @return The values accumulated in ODF format
   */

  def getAllvalues(sensor: database.DBSensor, starttime: Timestamp, interval: Double): xml.NodeSeq = {
    var node: xml.NodeSeq = xml.NodeSeq.Empty

    val infoitemvaluelist = {
      if (interval != -1) DataFormater.FormatSubData(sensor.path, starttime, interval, None)
      else SQLite.getNBetween(sensor.path, Some(starttime), None, None, None)
    }

    for (innersensor <- infoitemvaluelist) {
      node ++= <value dateTime={ innersensor.time.toString.replace(' ', 'T') }>{ innersensor.value }</value>
    }

    node
  }

  /**
   * Creates the right hierarchy from the infoitems that have been subscribed to. If sub has no callback (hascallback == false), get the values
   * accumulated between the sub starttime and current time.
   *
   * @param The paths of the infoitems that have been subscribed to
   * @param Index of the current 'level'. Used because it recursively drills deeper.
   * @return The ODF hierarchy as XML
   */

  def createFromPaths(paths: Array[Path], index: Int, starttime: Timestamp, interval: Double, hascallback: Boolean): xml.NodeSeq = {
    var node: xml.NodeSeq = xml.NodeSeq.Empty

    if (paths.isEmpty == false) {
      var slices = Buffer[Path]()
      var previous = paths.head

      for (path <- paths) {
        var slicedpath = Path(path.toSeq.slice(0, index + 1))
        SQLite.get(slicedpath) match {
          case Some(sensor: database.DBSensor) => {

            node ++=
              <InfoItem name={ sensor.path.last }>
              {
              if(hascallback) {<value dateTime={ sensor.time.toString.replace(' ', 'T') }>{ sensor.value }</value>}
              else {getAllvalues(sensor, starttime, interval)}
              }

              {
              val metaData = SQLite.getMetaData(sensor.path)
              if( metaData.isEmpty == false ) {XML.loadString(metaData.get)}
              else {xml.NodeSeq.Empty}
              }
              </InfoItem>

            }

          case Some(obj: database.DBObject) => {
            if (path(index) == previous(index)) {
              slices += path
            } else {
              node ++= <Object><id>{ previous(index) }</id>{ createFromPaths(slices.toArray, index + 1, starttime, interval, hascallback) }</Object>
              slices = Buffer[Path](path)
            }

          }

          case None => { node ++= <Error> Item not found in the database </Error> }
        }

        previous = path

        //in case this is the last item in the array, we check if there are any non processed paths left
        if (path == paths.last) {
          if (slices.isEmpty == false) {
            node ++= <Object><id>{ slices.last.toSeq(index) }</id>{ createFromPaths(slices.toArray, index + 1, starttime, interval, hascallback) }</Object>
          }
        }
      }

    }

    return node
  }

}