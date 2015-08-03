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
    Tags: Set[String],
    Address:String,
    Port: Int
  )
  type ServiceDescriptors = Map[String, ServiceDescriptor]

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
