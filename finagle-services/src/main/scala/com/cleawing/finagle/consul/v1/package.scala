package com.cleawing.finagle.consul

import org.json4s.CustomSerializer
import org.json4s.JsonDSL._
import org.json4s._
import java.util.Base64

package object v1 {
  case class NodeDescriptor
  (
    Node: String,
    Address: String
  )

  case class MemberDescriptor
  (
    Name: String,
    Addr: String,
    Port: Int,
    Tags: Map[String, String],
    Status: Int,
    ProtocolMin: Int,
    ProtocolMax: Int,
    ProtocolCur: Int,
    DelegateMin: Int,
    DelegateMax: Int,
    DelegateCur: Int
  )

  case class SelfDescriptor
  (
    Config: Map[String, Any], // TODO. Wrap Config in more detailed case class
    Member: MemberDescriptor
  )

  case class ServiceDescriptor
  (
    ID: String,
    Service: String,
    Tags: Option[Set[String]] = None,
    Address: Option[String] = None,
    Port: Option[Int] = None
  )
  type ServiceDescriptors = Map[String, ServiceDescriptor]

  case class NodeServiceDescriptor
  (
    Node: String,
    Address: String,
    ServiceID: String,
    ServiceName: String,
    ServiceTags: Set[String],
    ServiceAddress: String,
    ServicePort: Int
  )

  case class NodeServiceDescriptors
  (
    Node: NodeDescriptor,
    Services: Map[String, ServiceDescriptor]
  )

  case class CheckDescriptor
  (
    Node: String,
    CheckID: String,
    Name: String,
    Status: CheckState.Value,
    Notes: String,
    Output: String,
    ServiceID: String,
    ServiceName: String
  )
  type CheckDescriptors = Map[String, CheckDescriptor]

  object CheckState extends Enumeration {
    val any, unknown, passing, warning, critical = Value
  }

  case class HealthDescriptor
  (
    Node: NodeDescriptor,
    Service: ServiceDescriptor,
    Checks: Seq[CheckDescriptor]
  )

  case class RegisterCheck
  (
    Name: String,
    ID: Option[String] = None,
    Notes: Option[String] = None,
    Script: Option[String] = None,
    HTTP: Option[String] = None,
    Interval: Option[String] = None,
    TTL: Option[String] = None,
    ServiceId: Option[String] = None
  ) extends CheckValidation

  case class RegisterService
  (
    Name: String,
    ID: Option[String] = None,
    Tags: Option[Seq[String]] = None,
    Address: Option[String] = None,
    Port: Option[Int] = None,
    Check: Option[ServiceCheck] = None
  )

  case class ServiceCheck
  (
    Script: Option[String] = None,
    HTTP: Option[String] = None,
    Interval: Option[String] = None,
    TTL: Option[String] = None
  ) extends CheckValidation

  case class RegisterNode
  (
    Node: String,
    Address: String,
    Datacenter: Option[String] = None,
    Service: Option[ServiceDescriptor] = None,
    Check: Option[CheckUpdateDescriptor] = None
  ) {
    if (Service.isDefined && Check.isDefined)
      throw new IllegalArgumentException("Only Service or Check can be provided at the same time")
  }

  case class CheckUpdateDescriptor
  (
    Node: String,
    CheckID: String,
    Name: String,
    Notes: String,
    Status: String,
    ServiceID: String
  )

  case class DeregisterNode
  (
    Node: String,
    Datacenter: Option[String] = None,
    ServiceID: Option[String] = None,
    CheckID: Option[String] = None
  ) {
    if (ServiceID.isDefined && CheckID.isDefined)
      throw new IllegalArgumentException("Only ServiceID or CheckID can be provided at the same time")
  }

  case class SessionDescriptor
  (
    LockDelay: Option[String] = None,
    Name: Option[String] = None,
    Node: Option[String] = None,
    Checks: Option[Seq[String]] = None,
    Behavior: Option[SessionBehavior.Value] = None,
    TTL: Option[String] = None
  )

  case class SessionInfo
  (
    CreateIndex: Long,
    ID: String,
    Name: String,
    Node: String,
    Checks: Seq[String],
    LockDelay: Long,
    Behavior: SessionBehavior.Value,
    TTL: String
  )

  object SessionBehavior extends Enumeration {
    val release, delete = Value
  }

  case class KvValue
  (
    CreateIndex: Int,
    ModifyIndex: Int,
    LockIndex: Int,
    Key: String,
    Flags: Int,
    Value: Option[String],
    Session: Option[String]
  )

  object KvValueSerializer extends CustomSerializer[KvValue](formats => (
    {
      case JObject
        (
          JField("CreateIndex", JInt(createIndex)) ::
          JField("ModifyIndex", JInt(modifyIndex)) ::
          JField("LockIndex", JInt(lockIndex)) ::
          JField("Key", JString(key)) ::
          JField("Flags", JInt(flags)) ::
          JField("Value", JString(encodedValue)) ::
          JField("Session", JString(session)) :: Nil
        ) => KvValue(createIndex.toInt, modifyIndex.toInt, lockIndex.toInt, key, flags.toInt, Some(new String(Base64.getDecoder.decode(encodedValue))), Some(session))
      case JObject
        (
          JField("CreateIndex", JInt(createIndex)) ::
          JField("ModifyIndex", JInt(modifyIndex)) ::
          JField("LockIndex", JInt(lockIndex)) ::
          JField("Key", JString(key)) ::
          JField("Flags", JInt(flags)) ::
          JField("Value", JString(encodedValue)) :: Nil
        ) => KvValue(createIndex.toInt, modifyIndex.toInt, lockIndex.toInt, key, flags.toInt, Some(new String(Base64.getDecoder.decode(encodedValue))), None)
      case JObject
        (
          JField("CreateIndex", JInt(createIndex)) ::
          JField("ModifyIndex", JInt(modifyIndex)) ::
          JField("LockIndex", JInt(lockIndex)) ::
          JField("Key", JString(key)) ::
          JField("Flags", JInt(flags)) ::
          JField("Value", JNull) ::
          JField("Session", JString(session)) :: Nil
        ) => KvValue(createIndex.toInt, modifyIndex.toInt, lockIndex.toInt, key, flags.toInt, None, Some(session))
      case JObject
        (
          JField("CreateIndex", JInt(createIndex)) ::
          JField("ModifyIndex", JInt(modifyIndex)) ::
          JField("LockIndex", JInt(lockIndex)) ::
          JField("Key", JString(key)) ::
          JField("Flags", JInt(flags)) ::
          JField("Value", JNull) :: Nil
        ) => KvValue(createIndex.toInt, modifyIndex.toInt, lockIndex.toInt, key, flags.toInt, None, None)
    },
    PartialFunction.empty)
  )

  private[v1] trait CheckValidation {
    def Script: Option[String]
    def HTTP: Option[String]
    def Interval: Option[String]
    def TTL: Option[String]

    Seq(Script, HTTP, TTL).count(_.isDefined) match {
      case 0 => throw new IllegalArgumentException("One of Script, HTTP or TTL field should be set")
      case c if c > 1 => throw new IllegalArgumentException("Only one of Script, HTTP or TTL field should be set")
      case _ => // OK
    }
    (Script, Interval) match {
      case (Some(_), None) => throw new IllegalArgumentException("Interval required for Script check")
      case _ => // OK
    }
    (HTTP, Interval) match {
      case (Some(_), None) => throw new IllegalArgumentException("Interval required for HTTP check")
      case _ => // OK
    }
  }
}
