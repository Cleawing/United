package com.cleawing.akka.etcd

import akka.actor._
import mousio.client.retry._

object Etcd
  extends ExtensionId[EtcdExt]
  with ExtensionIdProvider {

  override def lookup() = Etcd
  override def createExtension(system: ExtendedActorSystem) = new EtcdExt(system)
}

private[etcd] class EtcdExt(system: ExtendedActorSystem) extends Extension {
  val settings = EtcdSettings(system)

  private val guardian = system.systemActorOf(EtcdGuardian.props(settings.peers, settings.retryPolicy), "etcd")
}

private[etcd] case class EtcdSettings (private val system: ActorSystem) {
  import scala.collection.JavaConversions._
  val config = system.settings.config.getConfig("akka.etcd")

  val peers: Seq[String] = config.getStringList("peers")

  val retryPolicy: RetryPolicy = config.getString("use-retry-policy") match {
    case "exponential-backoff" =>
      val policy = config.getConfig("retry-policy.exponential-backoff")
      new RetryWithExponentialBackOff(
        policy.getInt("start-delay"),
        policy.getInt("max-retry-count") match {
          case default if default <= 0 => -1
          case other => other
        },
        policy.getInt("max-delay") match {
          case default if default <= 0 => -1
          case other => other
        }
      )
    case "only-once" => new RetryOnce(config.getInt("retry-policy.only-once.delay-before-retry"))
    case "capped-max" =>
      val policy = config.getConfig(s"retry-policy.capped-max")
      new RetryNTimes(
        policy.getInt("delay-before-retry"),
        policy.getInt("max-retries")
      )
    case "until-elapsed" =>
      val policy = config.getConfig(s"retry-policy.until-elapsed")
      new RetryWithTimeout(
        policy.getInt("delay-before-retry"),
        policy.getInt("max-elapsed")
      )
  }
}
