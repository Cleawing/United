package com.cleawing.akka.curator

import java.nio.file.Paths

import akka.actor._
import com.cleawing.akka.LikeActorRefExtension
import org.apache.curator.RetryPolicy
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.ensemble.exhibitor.{DefaultExhibitorRestClient, ExhibitorEnsembleProvider, Exhibitors}
import org.apache.curator.ensemble.exhibitor.Exhibitors.BackupConnectionStringProvider
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider
import org.apache.curator.retry._
import org.apache.curator.utils.PathUtils

object Curator
  extends ExtensionId[CuratorExt]
  with ExtensionIdProvider {

  override def lookup() = Curator
  override def createExtension(system: ExtendedActorSystem) = new CuratorExt(system)
  override def get(system: ActorSystem): CuratorExt = super.get(system)

  object LeaderLatch {
    import recipes.LeaderLatch.CloseMode
    sealed trait Request

    case class Join(path: String, closeMode: CloseMode.Value = CloseMode.SILENT) extends Request
    case class Left(path: String) extends Request
  }

  object Reaper {
    import recipes.Reaper.Mode
    sealed trait Request

    case class AddPath(path: String, recursiveFrom: Option[String] = None, mode: Mode.Value = Mode.REAP_INDEFINITELY) extends Request
    object AddPath {
      def apply(path: String, mode: Mode.Value): AddPath = new AddPath(path, None, mode)
    }
    case class RemovePath(path: String) extends Request
  }

  // TODO. Throws IllegalArgumentException
  def buildPath(prefix: String, path: String) = PathUtils.validatePath(Paths.get("/", prefix, path).toString)
}

private[curator] class CuratorExt(val system: ExtendedActorSystem) extends LikeActorRefExtension {
  val settings = CuratorSettings(system)

  protected val target = system.systemActorOf(
    CuratorGuardian.props(
      settings.ensembleProvider,
      settings.retryPolicy,
      settings.namespace),
    "curator"
  )
}

private[curator] case class CuratorSettings (private val system: ActorSystem) {
  private val config = system.settings.config.getConfig("akka.curator")

  val namespace: String = s"akka/${if (config.getIsNull("namespace")) system.name else config.getString("namespace")}"

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

  val ensembleProvider: EnsembleProvider = {
    config.getString("use-ensemble-provider") match {
      case "fixed" => new FixedEnsembleProvider(config.getString("ensemble-provider.fixed.zk"))
      case "exhibitor" =>
        val provider = config.getConfig("ensemble-provider.exhibitor")
        val exhibitors = new Exhibitors(
          provider.getStringList("hosts"),
          provider.getInt("port"),
          new BackupConnectionStringProvider {
            val getBackupConnectionString = config.getString("ensemble-provider.fixed.zk")
          }
        )
        new ExhibitorEnsembleProvider(
          exhibitors,
          new DefaultExhibitorRestClient(),
          "/exhibitor/v1/cluster/list",
          provider.getDuration("polling-interval").toMillis.toInt,
          retryPolicy
        )
      case missedProvider => throw new IllegalArgumentException(s"Unsupported EnsembleProvider: $missedProvider")
    }
  }

  val reaperThreshold: Int = config.getDuration("reaper-threshold").toMillis.toInt
  val leaderLatchIdleTimeout: Long = config.getDuration("leader-latch-idle-timeout").toMillis
}
