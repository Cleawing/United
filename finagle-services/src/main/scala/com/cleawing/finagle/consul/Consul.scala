package com.cleawing.finagle.consul

import com.cleawing.finagle.http.{RamlClient, RamlHelper}
import scala.concurrent.{ExecutionContext, Future}

class Consul(host: String, port: Int = 8500)(implicit ec: ExecutionContext) {
  import RamlClient.paramOptToSeq

  object v1 {
    import com.cleawing.finagle.consul.v1._
    protected implicit val raml = RamlHelper("consul/v1.raml")
    protected implicit lazy val client = RamlClient(host, port, "consul")

    object agent {
      protected implicit val endpointUri = "/agent"
      def checks() = client.get[CheckDescriptors]("checks")
      def services() = client.get[ServiceDescriptors]("services")
      def members(wan: Boolean = false) = client.get[Seq[MemberDescriptor]]("members", queryParams = if(wan) Seq("wan" -> "1") else Seq())
      def self() = client.get[SelfDescriptor]("self")
      def maintenance(enable: Boolean, reason: Option[String] = None) =
        client.put[Boolean]("maintenance", queryParams = Seq("enable" -> enable.toString) ++ reason.map("reason" -> _))
      def join(address: String, wan: Boolean = false) = client.get[Boolean]("join/{address}", Seq("address" -> address), if(wan) Seq("wan" -> "1") else Seq())
      def forceLeave(nodeId: String) = client.get[Unit]("force-leave/{node}", Seq("node" -> nodeId))

      object check {
        protected implicit val endpointUri = "/agent/check"
        def register(name: String,
                     id: Option[String] = None,
                     notes: Option[String] = None,
                     script: Option[String] = None,
                     http: Option[String] = None,
                     interval: Option[String] = None,
                     ttl: Option[String] = None,
                     serviceId: Option[String] = None)  =
          try {
            client.put[Boolean]("register", payload = Some(RegisterCheck(name, id, notes, script, http, interval, ttl, serviceId)))
          } catch {
            case t: Throwable => Future.failed(t)
          }
        def deRegister(checkId: String) = client.get[Boolean]("deregister/{checkid}", Seq("checkid" -> checkId))
        def pass(checkId: String, note: Option[String] = None) = client.get[Boolean]("pass/{checkid}", Seq("checkid" -> checkId), note.map("note" -> _))
        def warn(checkId: String, note: Option[String] = None) = client.get[Boolean]("warn/{checkid}", Seq("checkid" -> checkId), note.map("note" -> _))
        def fail(checkId: String, note: Option[String] = None) = client.get[Boolean]("fail/{checkid}", Seq("checkid" -> checkId), note.map("note" -> _))
      }

      object service {
        protected implicit val endpointUri = "/agent/service"
        def register(name: String,
                     id: Option[String] = None,
                     tags: Option[Seq[String]] = None,
                     address: Option[String] = None,
                     port: Option[Int] = None,
                     scriptCheck : Option[String] = None,
                     httpCheck : Option[String] = None,
                     ttlCheck: Option[String] = None,
                     interval: Option[String] = None) = {
          val check = if  (scriptCheck.isDefined || httpCheck.isDefined || ttlCheck.isDefined)
            Some(ServiceCheck(scriptCheck, httpCheck, interval, ttlCheck))
          else None
          client.put[Boolean]("register", payload = Some(RegisterService(name, id, tags, address, port, check)))
        }
        def deRegister(serviceId: String) = client.get[Boolean]("deregister/{serviceid}", Seq("serviceid" -> serviceId))
        def maintenance(serviceId: String, enable: Boolean, reason: Option[String] = None) =
          client.put[Boolean]("maintenance/{serviceid}", Seq("serviceid" -> serviceId), Seq("enable" -> enable.toString) ++ reason.map("reason" -> _))
      }
    }

    object catalog {
      protected implicit val endpointUri = "/catalog"
      // Register and deRegister - not tested
      def register(descriptor: RegisterNode) = client.put[Boolean]("register", payload = Some(descriptor))
      def deRegister(descriptor: DeregisterNode) = client.put[Boolean]("deregister", payload = Some(descriptor))
      def datacenters() = client.get[Seq[String]]("datacenters")
      def nodes() = client.get[Seq[NodeDescriptor]]("nodes")
      def services() = client.get[Map[String, Set[String]]]("services")
      def service(serviceName: String, dc: Option[String] = None, tag: Option[String] = None) =
        client.get[Seq[NodeServiceDescriptor]](
          "service/{service}",
          Seq("service" -> serviceName),
          dc.map("dc" -> _) ++ tag.map("tag" -> _)
        )
      def node(nodeId: String, dc: Option[String] = None) =
        client.get[NodeServiceDescriptors](
          "node/{node}",
          Seq("node" -> nodeId),
          dc.map("dc" -> _)
        )
    }

    object status {
      protected implicit val endpointUri = "/status"
      def leader() = client.get[String]("leader")
      def peers() = client.get[Seq[String]]("peers")
    }

    object health {
      protected implicit val endpointUri = "/health"
      def node(nodeId: String, dc: Option[String] = None) =
        client.get[Seq[CheckDescriptor]]("node/{node}", Seq("node" -> nodeId), dc.map("dc" -> _))
      def checks(serviceId: String, dc: Option[String] = None) =
        client.get[Seq[CheckDescriptor]]("checks/{service}", Seq("service" -> serviceId), dc.map("dc" -> _))
      def service(serviceName: String, dc: Option[String] = None, tag: Option[String] = None, passing: Boolean = false) =
        client.get[Seq[HealthDescriptor]](
          "service/{service}",
          Seq("service" -> serviceName),
          dc.map("dc" -> _) ++ tag.map("tag" -> _) ++ (if(passing) Seq("passing" -> "true") else Seq()))
      def state(stateId: String, dc: Option[String] = None) =
        client.get[Seq[CheckDescriptor]]("state/{state}", Seq("state" -> stateId), dc.map("dc" -> _))
    }

    def close(): Future[Unit] = client.close()
  }
}

object Consul {
  def apply(host: String, port: Int = 8500)(implicit ec: ExecutionContext) : Consul = new Consul(host, port)
}
