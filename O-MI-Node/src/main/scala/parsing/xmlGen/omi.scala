// Generated by <a href="http://scalaxb.org/">scalaxb</a>.
package parsing 
package xmlGen
package xmlTypes

import java.net.URI
import javax.xml.datatype.XMLGregorianCalendar

import scala.collection.immutable.HashMap

case class OmiEnvelopeType(omienvelopetypeoption: scalaxb.DataRecord[xmlTypes.OmiEnvelopeTypeOption],
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) {
  lazy val version: String = attributes("@version").as[String]
  lazy val ttl: String ={
    val dr = attributes("@ttl")
    dr.value match{
      case _: String => dr.as[String]
      case _: Long => dr.as[Long].toString
      case other => other.toString
    }
  }
}

      

trait OmiEnvelopeTypeOption

/** Payload for the protocol
*/
case class MsgType(mixed: Seq[scalaxb.DataRecord[Any]] = Vector.empty)
      


/** Base type for "read" and "write" requests.
*/
trait RequestBaseType {
  def nodeList: Option[xmlTypes.NodesType]
  def requestID: Seq[String]
  def msg: Option[xmlTypes.MsgType]
  def callback: Option[java.net.URI]
  def msgformat: Option[String]
  def targetType: xmlTypes.TargetTypeType
}

trait TargetTypeType

object TargetTypeType {
  def fromString(
    value: String,
    scope: scala.xml.NamespaceBinding
  )(implicit fmt: scalaxb.XMLFormat[xmlTypes.TargetTypeType]): TargetTypeType = fmt.reads(scala.xml.Text(value), Vector.empty.toList) match {
    case Right(x: TargetTypeType) => x
    case x => throw new RuntimeException(s"fromString returned unexpected value $x for input $value")
  }
}

case object Device extends TargetTypeType { override def toString = "device" }
case object Node extends TargetTypeType { override def toString = "node" }


case class ReadRequestType(nodeList: Option[xmlTypes.NodesType] = None,
  requestID: Seq[String] = Vector.empty,
  msg: Option[xmlTypes.MsgType] = None,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) extends RequestBaseType with OmiEnvelopeTypeOption {
  lazy val callback: Option[URI] = attributes.get("@callback") map { _.as[java.net.URI]}
  lazy val msgformat: Option[String] = attributes.get("@msgformat") map { _.as[String]}
  lazy val targetType: TargetTypeType = attributes("@targetType").as[TargetTypeType]
  lazy val interval: Option[String] = attributes.get("@interval") map { _.as[String]}
  lazy val oldest: Option[BigInt] = attributes.get("@oldest") map { _.as[BigInt]}
  lazy val begin: Option[XMLGregorianCalendar] = attributes.get("@begin") map { _.as[javax.xml.datatype.XMLGregorianCalendar]}
  lazy val end: Option[XMLGregorianCalendar] = attributes.get("@end") map { _.as[javax.xml.datatype.XMLGregorianCalendar]}
  lazy val newest: Option[BigInt] = attributes.get("@newest") map { _.as[BigInt]}
  lazy val all: Option[Boolean] = attributes.get("@all") map { _.as[Boolean]}
}

      


case class WriteRequestType(nodeList: Option[xmlTypes.NodesType] = None,
  requestID: Seq[String] = Vector.empty,
  msg: Option[xmlTypes.MsgType] = None,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) extends RequestBaseType with OmiEnvelopeTypeOption {
  lazy val callback: Option[URI] = attributes.get("@callback") map { _.as[java.net.URI]}
  lazy val msgformat: Option[String] = attributes.get("@msgformat") map { _.as[String]}
  lazy val targetType: TargetTypeType = attributes("@targetType").as[TargetTypeType]
}

      


/** List of results.
*/
case class ResponseListType(result: Seq[xmlTypes.RequestResultType] = Vector.empty) extends OmiEnvelopeTypeOption
      


/** Call request type.
*/
case class CallRequestType(nodeList: Option[xmlTypes.NodesType] = None,
  requestID: Seq[String] = Vector.empty,
  msg: Option[xmlTypes.MsgType] = None,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) extends RequestBaseType with OmiEnvelopeTypeOption {
  lazy val callback: Option[URI] = attributes.get("@callback") map { _.as[java.net.URI]}
  lazy val msgformat: Option[String] = attributes.get("@msgformat") map { _.as[String]}
  lazy val targetType: TargetTypeType = attributes("@targetType").as[TargetTypeType]
}

      


/** Delete request type.
*/
case class DeleteRequestType(nodeList: Option[xmlTypes.NodesType] = None,
  requestID: Seq[String] = Vector.empty,
  msg: Option[xmlTypes.MsgType] = None,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) extends RequestBaseType with OmiEnvelopeTypeOption {
  lazy val callback: Option[URI] = attributes.get("@callback") map { _.as[java.net.URI]}
  lazy val msgformat: Option[String] = attributes.get("@msgformat") map { _.as[String]}
  lazy val targetType: TargetTypeType = attributes("@targetType").as[TargetTypeType]
}

      


/** Result of a request.
*/
case class RequestResultType(
  returnValue: xmlTypes.ReturnType,
  requestID: Seq[xmlTypes.IdType] = Vector.empty,
  msg: Option[xmlTypes.MsgType] = None,
  nodeList: Option[xmlTypes.NodesType] = None,
  omiEnvelope: Option[xmlTypes.OmiEnvelopeType] = None,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) {
  lazy val msgformat: Option[String] = attributes.get("@msgformat") map { _.as[String]}
  lazy val targetType: TargetTypeType = attributes("@targetType").as[TargetTypeType]
}

      


/** Return status of request. Use HTTP codes / descriptions when applicable.
*/
case class ReturnType(value: String,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) {
  lazy val returnCode: String = attributes("@returnCode").as[String]
  lazy val description: Option[String] = attributes.get("@description") flatMap { _.as[Option[String]]}
}

      


/** The nodesType is used anywhere in the schema where lists of nodes can appear. 
*/
case class NodesType(node: Seq[java.net.URI] = Vector.empty,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) {
  lazy val typeValue: Option[String] = attributes.get("@type") map { _.as[String]}
}

      


/** Some kind of identifier with optional "format" attribute for indicating what kind of identifier is used. 
*/
case class IdType(value: String,
  attributes: Map[String, scalaxb.DataRecord[Any]] = Map()) {
  lazy val format: Option[String] = attributes.get("@format") map { _.as[String]}
}

      


case class CancelRequestType(nodeList: Option[xmlTypes.NodesType] = None,
  requestID: Seq[xmlTypes.IdType] = Vector.empty) extends OmiEnvelopeTypeOption
      
