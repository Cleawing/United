package com.cleawing.akka.consul.api

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, Uri, HttpRequest}
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

object Marshallers {
  import Request._
  private val baseUri = "/v1"
  private type TRM[T] = ToRequestMarshaller[T]

  protected implicit val formats = Serialization.formats(NoTypeHints)

  implicit def fromDataRequest : TRM[Request] = {
    Marshaller.strict {
      case r: StatusRequest =>
        implicit val endpoint = s"$baseUri/status"
        r match {
          case Status.Get.Leader =>
            Marshalling.Opaque(() => uriRequest("leader"))
          case Status.Get.Peers =>
            Marshalling.Opaque(() => uriRequest("peers"))
        }
      case r: AgentRequest =>
        implicit val endpoint = s"$baseUri/agent"
        r match {
          case Agent.Get.Checks =>
            Marshalling.Opaque(() => uriRequest("checks"))
          case Agent.Get.Services =>
            Marshalling.Opaque(() => uriRequest("services"))
          case Agent.Get.Members =>
            Marshalling.Opaque(() => uriRequest("members"))
          case Agent.Get.Self =>
            Marshalling.Opaque(() => uriRequest("self"))
          case Agent.Put.Maintenance(enable, reason) =>
            lazy val params = Seq(("enable", enable.toString)) ++ reason.map("reason" -> _)
            Marshalling.Opaque(() => uriRequest("maintenance", params: _*).withMethod(HttpMethods.PUT))
          case Agent.Get.Join(address, wan) =>
            lazy val params = if (wan) Seq(("wan", "1")) else Seq()
            Marshalling.Opaque(() => uriRequest(s"join/$address", params: _*))
          case Agent.Get.ForceLeave(node) =>
            Marshalling.Opaque(() => uriRequest(s"force-leave/$node"))
          case register : Agent.Check.Put.RegisterCheck =>
            Marshalling.Opaque(() => uriRequest("check/register")
              .withMethod(HttpMethods.PUT)
              .withEntity(ContentTypes.`application/json`, Serialization.write(register))
            )
          case Agent.Check.Get.Deregister(checkId) =>
            Marshalling.Opaque(() => uriRequest(s"check/deregister/$checkId"))
          case Agent.Check.Get.Pass(checkId, note) =>
            lazy val params = Seq() ++ note.map("note" -> _)
            Marshalling.Opaque(() => uriRequest(s"check/pass/$checkId", params: _*))
          case Agent.Check.Get.Warn(checkId, note) =>
            lazy val params = Seq() ++ note.map("note" -> _)
            Marshalling.Opaque(() => uriRequest(s"check/warn/$checkId", params: _*))
          case Agent.Check.Get.Fail(checkId, note) =>
            lazy val params = Seq() ++ note.map("note" -> _)
            Marshalling.Opaque(() => uriRequest(s"check/fail/$checkId", params: _*))
          case register : Agent.Service.Put.RegisterService =>
            Marshalling.Opaque(() => uriRequest("service/register")
              .withMethod(HttpMethods.PUT)
              .withEntity(ContentTypes.`application/json`, Serialization.write[Agent.Service.Put.RegisterService](register))
            )
          case Agent.Service.Get.Deregister(serviceId) =>
            Marshalling.Opaque(() => uriRequest(s"service/deregister/$serviceId"))
          case Agent.Service.Put.Maintenance(serviceId, enable, reason) =>
            lazy val params = Seq(("enable", enable.toString)) ++ reason.map("reason" -> _)
            Marshalling.Opaque(() => uriRequest(s"service/maintenance/$serviceId", params: _*).withMethod(HttpMethods.PUT))
        }
    }
  }

  private def uriRequest(uri: String, kvp: (String, String)*)(implicit endpoint: String) : HttpRequest = {
    HttpRequest(uri = Uri(s"$endpoint/$uri").withQuery(kvp: _*))
  }
}
