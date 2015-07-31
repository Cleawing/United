package com.cleawing.consul

import akka.http.scaladsl.model.StatusCode

package object api {
  case class ApiException(message: String, statusCode: Option[StatusCode]) extends Exception(message)

  sealed trait Request
  sealed trait AgentRequest extends Request
  sealed trait StatusRequest extends Request

  private[api] trait CheckValidation {
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

  object Request {
    object Agent {
      object Get {
        case object Checks extends AgentRequest
        case object Services extends AgentRequest
        case object Members extends AgentRequest
        case object Self extends AgentRequest
        case class Join(address: String, wan: Boolean = false) extends AgentRequest
        case class ForceLeave(node: String) extends AgentRequest
      }
      object Put {
        case class Maintenance(enable: Boolean, reason: Option[String] = None) extends AgentRequest
      }
      object Check {
        object Put {
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
          ) extends AgentRequest with CheckValidation
        }
        object Get {
          case class Deregister(checkId: String) extends AgentRequest
          case class Pass(checkId: String, note: Option[String] = None) extends AgentRequest
          case class Warn(checkId: String, note: Option[String] = None) extends AgentRequest
          case class Fail(checkId: String, note: Option[String] = None) extends AgentRequest
        }
      }
      object Service {
        object Put {
          case class RegisterService
          (
            Name: String,
            ID: Option[String] = None,
            Tags: Option[Seq[String]] = None,
            Address: Option[String] = None,
            Port: Option[Int] = None,
            Check: Option[ServiceCheck] = None
          ) extends AgentRequest
          case class ServiceCheck
          (
            Script: Option[String] = None,
            HTTP: Option[String] = None,
            Interval: Option[String] = None,
            TTL: Option[String] = None
          ) extends CheckValidation

          case class Maintenance(serviceId: String, enable: Boolean, reason: Option[String] = None) extends AgentRequest
        }
        object Get {
          case class Deregister(serviceId: String) extends AgentRequest
        }
      }
    }

    object Status {
      object Get {
        case object Leader extends StatusRequest
        case object Peers extends StatusRequest
      }
    }
  }

  object Response {
    case class Self(Config: Map[String, Any], Member: Member)

    case class Member
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
    type Members = Seq[Member]

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

    case class ServiceDescriptor
    (
      ID: String,
      Service: String,
      Tags: Set[String],
      Address:String,
      Port: Int
    )
    type ServiceDescriptors = Map[String, ServiceDescriptor]
  }

}
