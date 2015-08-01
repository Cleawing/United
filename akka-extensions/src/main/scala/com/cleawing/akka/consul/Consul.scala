package com.cleawing.akka.consul

import java.util.UUID

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._

object Consul
  extends ExtensionId[ConsulExt]
  with ExtensionIdProvider {

  override def lookup() = Consul
  override def createExtension(system: ExtendedActorSystem) = new ConsulExt(system)
}

class ConsulExt(system: ExtendedActorSystem) extends Extension {
  import api.ConsulGuardian
  import api.Response._

  val config = system.settings.config.getConfig("consul")
  val nodeId = UUID.randomUUID()
  val serviceId = s"akka-$nodeId"
  val serviceName = s"akka-${system.name}"

  private val guardian = system.systemActorOf(
    ConsulGuardian.props(config.getString("host"), config.getInt("port")),
    "consul"
  )
  import system.dispatcher

  system.scheduler.schedule(0.second, 1.seconds, guardian, ConsulGuardian.UpdateTTL)

  object agent {
    import api.Request.Agent._

    def checks() : Future[CheckDescriptors] = request(Get.Checks).mapTo[CheckDescriptors]
    def services() : Future[ServiceDescriptors] = request(Get.Services).mapTo[ServiceDescriptors]
    def members() : Future[Members] = request(Get.Members).mapTo[Members]
    def self() : Future[Self] = request(Get.Self).mapTo[Self]
    def maintenance(enable: Boolean, reason: Option[String] = None) : Future[Boolean] =
      request(Put.Maintenance(enable, reason)).mapTo[Boolean]
    def join(address: String,
             wan: Boolean = false) = request(Get.Join(address, wan)).mapTo[Boolean]
    def forceLeave(node: String) : Future[Unit] = request(Get.ForceLeave(node)).mapTo[Unit]

    object check {
      def register(name: String,
                   id: Option[String] = None,
                   notes: Option[String] = None,
                   script: Option[String] = None,
                   http: Option[String] = None,
                   interval: Option[String] = None,
                   ttl: Option[String] = None,
                   serviceId: Option[String] = None) : Future[Boolean] =
        request(Check.Put.RegisterCheck(name, id, notes, script, http, interval, ttl, serviceId)).mapTo[Boolean]
      def deRegister(checkId: String) : Future[Boolean] = request(Check.Get.Deregister(checkId)).mapTo[Boolean]
      def pass(checkId: String, note: Option[String] = None) : Future[Boolean] = request(Check.Get.Pass(checkId, note)).mapTo[Boolean]
      def warn(checkId: String, note: Option[String] = None) : Future[Boolean] = request(Check.Get.Warn(checkId, note)).mapTo[Boolean]
      def fail(checkId: String, note: Option[String] = None) : Future[Boolean] = request(Check.Get.Fail(checkId, note)).mapTo[Boolean]
    }

    object service {
      def register(name: String,
                   id: Option[String] = None,
                   tags: Option[Seq[String]] = None,
                   address: Option[String] = None,
                   port: Option[Int] = None,
                   scriptCheck : Option[String] = None,
                   httpCheck : Option[String] = None,
                   ttlCheck: Option[String] = None,
                   interval: Option[String] = None) : Future[Boolean] = {
        val check = if  (scriptCheck.isDefined || httpCheck.isDefined || ttlCheck.isDefined)
          Some(Service.Put.ServiceCheck(scriptCheck, httpCheck, interval, ttlCheck))
          else None
        request(Service.Put.RegisterService(name, id, tags, address, port, check)).mapTo[Boolean]
      }
      def deRegister(serviceId: String) : Future[Boolean] = request(Service.Get.Deregister(serviceId)).mapTo[Boolean]
      def maintenance(serviceId: String, enable: Boolean, reason: Option[String] = None) : Future[Boolean] =
        request(Service.Put.Maintenance(serviceId, enable, reason)).mapTo[Boolean]
    }
  }

  object status {
    import api.Request.Status._
    def leader() : Future[String] = request(Get.Leader).mapTo[String]
    def peers() : Future[Seq[String]] = request(Get.Peers).mapTo[Seq[String]]
  }

  // TODO. Grab timeout from config
  protected implicit val timeout = Timeout(5.seconds)

  private def request(req: com.cleawing.akka.consul.api.Request) : Future[_] = {
    guardian.ask(req)
  }
}
