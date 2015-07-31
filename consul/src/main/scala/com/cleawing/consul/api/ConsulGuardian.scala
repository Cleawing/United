package com.cleawing.consul.api

import akka.actor.{Actor, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Future
import scala.util.{Failure, Success}

import com.cleawing.akka.HttpClientPool

class ConsulGuardian(val host: String, val port: Int)
  extends Actor with HttpClientPool[Request] {

  import Request._
  import Response._
  import Marshallers._
  import Unmarshallers._

  import context.dispatcher

  def receive = {
    case request : Request =>
      val origin = sender()

      makeRequest(request).flatMap {
        case (Failure(e), None) => Future((origin, akka.actor.Status.Failure(e)))
        case (r, Some(target)) => r match {
          case Failure(e) => Future((target, akka.actor.Status.Failure(e)))
          case Success(response) => response.status match {
            case StatusCodes.OK =>
              (request match {
                // /v1/status
                case Status.Get.Leader => Unmarshal(response.entity).to[String]
                case Status.Get.Peers => Unmarshal(response.entity).to[Seq[String]]
                // /v1/agent
                case Agent.Get.Checks => Unmarshal(response.entity).to[CheckDescriptors]
                case Agent.Get.Services => Unmarshal(response.entity).to[ServiceDescriptors]
                case Agent.Get.Members => Unmarshal(response.entity).to[Members]
                case Agent.Get.Self => Unmarshal(response.entity).to[Self]
                case _: Agent.Put.Maintenance | _: Agent.Get.Join => Future(true)
                case _: Agent.Get.ForceLeave => Future(())
                case _: Agent.Check.Put.RegisterCheck | _: Agent.Check.Get.Deregister => Future(true)
                case _: Agent.Check.Get.Pass | _: Agent.Check.Get.Warn | _: Agent.Check.Get.Fail => Future(true)
                case _: Agent.Service.Put.RegisterService | _: Agent.Service.Get.Deregister | _: Agent.Service.Put.Maintenance => Future(true)
              }).map((target, _))
            case notOk =>
              request match {
                case _: Agent.Put.Maintenance => Future((target, false)) // TODO. Real needed?
                case _ => Unmarshal(response.entity).to[String].map(reason => (target, akka.actor.Status.Failure(ApiException(reason, Some(notOk)))))
              }
          }
        }
        case _ => Future((context.system.deadLetters, None))
      }.recover{
        case t: Throwable => (origin, akka.actor.Status.Failure(t))
      }.foreach {
        case(_, None) => // ignore
        case (target, payload) => target ! payload
      }
  }
}

object ConsulGuardian {
  def props(host: String, port: Int) : Props = Props(classOf[ConsulGuardian], host, port)
}
