package com.cleawing.finagle.consul

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.ext.EnumNameSerializer
import com.cleawing.finagle.http.{RamlClient, RamlHelper}
import scala.concurrent.{ExecutionContext, Future}

class Consul(host: String, port: Int = 8500)(implicit ec: ExecutionContext) {
  import RamlClient.paramOptToSeq

  object v1 {
    import com.cleawing.finagle.consul.v1._
    protected implicit val formats = Serialization.formats(NoTypeHints) +
      new EnumNameSerializer(CheckState) + new EnumNameSerializer(SessionBehavior) + KvValueSerializer + EventSerializer
    protected implicit val raml = RamlHelper("consul/v1.raml")
    protected implicit lazy val client = RamlClient(host, port, "consul")

    object kv {
      protected implicit val endpointUri = "/kv"
      def get(key: String, recurse: Boolean = false, dc: Option[String] = None) =
        client.get[Seq[KvValue]](
          "{key}",
          Seq("key" -> key),
          dc.map("dc" -> _) ++ Option(recurse).collect{ case true => "recurse" -> "true" }
        ).recover {
          case e: RamlClient.ResponseError => (e.getMessage, e.response.getStatusCode()) match {
            case ("", 404) => throw e.copy(message = "Not Found")
            case _ => throw e
          }
          case t: Throwable => throw t
        }
      def getRaw(key: String, dc: Option[String] = None) = {
        client.get[String](
          "{key}",
          Seq("key" -> key),
          Seq("raw" -> "true") ++  dc.map("dc" -> _)
        )
      }
      def getKeys(key: String, separator: Option[String] = None, dc: Option[String] = None) =
        client.get[Seq[String]](
          "{key}",
          Seq("key" -> key),
          Seq("keys" -> "true") ++ separator.map("separator" -> _) ++  dc.map("dc" -> _)
        ).map(r => r.filterNot(_ == key))
      def put(key: String, value: String, flags: Option[Int] = None, acquire: Option[String] = None, release: Option[String] = None, dc: Option[String] = None) =
        client.put[Boolean](
          "{key}",
          Seq("key" -> key),
          flags.map("flags" -> _.toString) ++ acquire.map("acquire" -> _) ++ release.map("release" -> _) ++ dc.map("dc" -> _),
          Some(value)
        )
      def delete(key: String, cas: Option[Int] = None, recurse : Boolean = false, dc: Option[String] = None) =
        client.delete[Boolean](
          "{key}",
          Seq("key" -> key),
          cas.map("cas" -> _.toString) ++ dc.map("dc" -> _) ++ Option(recurse).collect{ case true => "recurse" -> "true" }
        )
    }

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
      def nodes(dc: Option[String] = None) = client.get[Seq[NodeDescriptor]]("nodes", queryParams = dc.map("dc" -> _))
      def services(dc: Option[String] = None) = client.get[Map[String, Set[String]]]("services", queryParams = dc.map("dc" -> _))
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

    object session {
      protected implicit val endpointUri = "/session"
      def create(lockDelay: Option[String] = None,
                 name: Option[String] = None,
                 node: Option[String] = None,
                 checks: Option[Seq[String]] = None,
                 behavior: Option[SessionBehavior.Value] = None,
                 ttl: Option[String] = None,
                 dc: Option[String] = None) =
      client.put[Map[String, String]](
        "create",
        queryParams = dc.map("dc" -> _),
        payload = Some(SessionDescriptor(lockDelay, name, node, checks, behavior, ttl))
      ).map(_("ID"))
      def destroy(sessionId: String, dc: Option[String] = None) =
        client.put[Boolean](
          "destroy/{session}",
          Seq("session" -> sessionId),
          dc.map("dc" -> _)
        )
      def info(sessionId: String, dc: Option[String] = None) =
        client.get[Seq[SessionInfo]](
          "info/{session}",
          Seq("session" -> sessionId),
          dc.map("dc" -> _)
        ).map(_.head)
      def node(nodeId: String, dc: Option[String] = None) =
        client.get[Seq[SessionInfo]](
          "node/{node}",
          Seq("node" -> nodeId),
          dc.map("dc" -> _)
        )
      def list(dc: Option[String] = None) =
        client.get[Seq[SessionInfo]](
          "list",
          queryParams =dc.map("dc" -> _)
        )
      def renew(sessionId: String, dc: Option[String] = None) =
        client.put[Seq[SessionInfo]](
          "renew/{session}",
          Seq("session" -> sessionId),
          dc.map("dc" -> _)
        ).map(_.head)
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
      def state(state: CheckState.Value, dc: Option[String] = None) =
        client.get[Seq[CheckDescriptor]]("state/{state}", Seq("state" -> state.toString), dc.map("dc" -> _))
    }

    object event {
      protected implicit val endpointUri = "/event"
      def fire(name: String, payload: String = "", node: Option[String] = None, service: Option[String] = None, tag: Option[String] = None, dc: Option[String] = None) =
        client.put[Event](
          "fire/{name}",
          Seq("name" -> name),
          node.map("node" -> _) ++ service.map("service" -> _) ++ tag.map("tag" -> _) ++ dc.map("dc" -> _),
          Some(payload)
        )
      def list(name: Option[String] = None, node: Option[String] = None, service: Option[String] = None, tag: Option[String] = None) =
        client.get[Seq[Event]](
          "list",
          queryParams = name.map("name" -> _ ) ++ node.map("node" -> _) ++ service.map("service" -> _) ++ tag.map("tag" -> _)
        )
    }

    object status {
      protected implicit val endpointUri = "/status"
      def leader() = client.get[String]("leader")
      def peers() = client.get[Seq[String]]("peers")
    }

    def close(): Future[Unit] = client.close()
  }
}

object Consul {
  def apply(host: String, port: Int = 8500)(implicit ec: ExecutionContext) : Consul = new Consul(host, port)
}
