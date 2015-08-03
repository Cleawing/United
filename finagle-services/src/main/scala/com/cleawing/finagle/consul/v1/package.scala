package com.cleawing.finagle.consul

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
    Status: String,
    Notes: String,
    Output: String,
    ServiceID: String,
    ServiceName: String
  )
  type CheckDescriptors = Map[String, CheckDescriptor]

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
