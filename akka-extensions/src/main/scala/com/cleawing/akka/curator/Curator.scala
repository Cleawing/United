package com.cleawing.akka.curator

import akka.actor._
import org.apache.curator.RetryPolicy
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider
import org.apache.curator.retry._

object Curator
  extends ExtensionId[CuratorExt]
  with ExtensionIdProvider {

  override def lookup() = Curator
  override def createExtension(system: ExtendedActorSystem) = new CuratorExt(system)
}

private[curator] class CuratorExt(system: ExtendedActorSystem) extends Extension {
  val settings = CuratorSettings(system)

  private val guardian = system.systemActorOf(
    CuratorGuardian.props(
      settings.ensembleProvider,
      settings.retryPolicy,
      settings.namespace),
    "curator"
  )
}

private[curator] case class CuratorSettings (private val system: ActorSystem) {
  private val config = system.settings.config.getConfig("akka.curator")

  val namespace: String = if (config.getIsNull("namespace")) system.name else config.getString("namespace")

  val ensembleProvider: EnsembleProvider = {
    config.getString("use-ensemble-provider") match {
      case "fixed" => new FixedEnsembleProvider(config.getString("ensemble-provider.fixed.zk"))
      // case "exhibitor" => TODO. Add ExhibitorEnsembleProvider support
      case unsupported => throw new IllegalArgumentException(s"Unsupported EnsembleProvider: $unsupported")
    }
  }

  val retryPolicy: RetryPolicy = config.getString("use-retry-policy") match {
    case "exponential-backoff" =>
      val policy = config.getConfig("retry-policy.exponential-backoff")
      new ExponentialBackoffRetry(
        policy.getInt("base-sleep"),
        policy.getInt("max-retries"),
        policy.getInt("max-sleep") match {
          case default if default <= 0 => Int.MaxValue
          case other => other
        }
      )
    case "bounded-exponential-backoff" =>
      val policy = config.getConfig("retry-policy.bounded-exponential-backoff")
      new BoundedExponentialBackoffRetry(
        policy.getInt("base-sleep"),
        policy.getInt("max-sleep"),
        policy.getInt("max-retries")
      )
    case "only-once" => new RetryOneTime(config.getInt("retry-policy.one-time.sleep-between"))
    case "capped-max" =>
      val policy = config.getConfig("retry-policy.bounded-times")
      new RetryNTimes(
        policy.getInt("max-retries"),
        policy.getInt("sleep-between")
      )
    case "until-elapsed" =>
      val policy = config.getConfig("retry-policy.until-elapsed")
      new RetryUntilElapsed(
        policy.getInt("max-elapsed"),
        policy.getInt("sleep-between")
      )
    case unsupported => throw new IllegalArgumentException(s"Unsupported RetryPolicy: $unsupported")
  }
}
