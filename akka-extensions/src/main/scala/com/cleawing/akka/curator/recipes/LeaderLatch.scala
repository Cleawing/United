package com.cleawing.akka.curator.recipes

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import com.cleawing.akka.curator.Curator
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.utils.ZKPaths

import scala.concurrent.duration._
import scala.collection.concurrent

private[curator] class LeaderLatch(curator: CuratorFramework) extends Actor with ActorLogging {
  import LeaderLatch._
  private val latchers = concurrent.TrieMap.empty[String, ActorRef]
  private val latchersCounter = new AtomicInteger(0)

  def receive = {
    case Curator.LeaderLatch.Join(path, closeMode) =>
      try {
        val internalPath = Curator.buildPath(pathPrefix, path)
        latchers.getOrElseUpdate(
          internalPath,
          context.watch(
            context.actorOf(
              Watcher.props(
                curator,
                internalPath,
                closeMode,
                Curator(context.system).settings.leaderLatchIdleTimeout.milliseconds
              ),
              latchersCounter.incrementAndGet().toString
            )
          )
        ) forward Watcher.Subscribe
      } catch {
        case e: IllegalArgumentException => sender().tell(akka.actor.Status.Failure(e), Actor.noSender)
        case t: Throwable => throw t
      }
    case Curator.LeaderLatch.Left(path) =>
      val internalPath = Curator.buildPath(pathPrefix, path)
      if (latchers.contains(internalPath))
        latchers(internalPath) forward Watcher.UnSubscribe
      else {
        log.warning("LeaderLatch not started for path: {}", internalPath)
      }
    case Terminated(ref) =>
      latchers.collect {
        case (path, watcher) if ref == watcher =>
          try {
            val fullPath = ZKPaths.fixForNamespace(Curator(context.system).settings.namespace, path)
            if (ZKPaths.getSortedChildren(curator.getZookeeperClient.getZooKeeper, fullPath).isEmpty) {
              ZKPaths.deleteChildren(curator.getZookeeperClient.getZooKeeper, fullPath, true)
            }
          } catch {
            case t: Throwable =>
              log.error(t, "Zookeeper error during direct path delete")
          }
          path
      }.foreach(latchers.remove)
  }
}

object LeaderLatch {
  object CloseMode extends Enumeration {
    val SILENT, NOTIFY_LEADER = Value
  }

  case class LeaderPath(path: String)
  case class IsLeader(path: String)
  case class NotLeader(path: String)

  private[curator] def props(curator: CuratorFramework): Props  =
    Props(classOf[LeaderLatch], curator)

  private[LeaderLatch] val pathPrefix = "leaderLatches"

  private[LeaderLatch] class Watcher(curator: CuratorFramework,
                                     path: String, closeMode: CloseMode.Value,
                                     idleTimeout: FiniteDuration) extends Actor with ActorLogging {
    import org.apache.curator.framework.recipes.leader
    import Watcher._
    import scala.collection.mutable
    import context.dispatcher

    private val subscribers = mutable.Set.empty[ActorRef]
    private var noSubscribersTimer : Cancellable = _

    private val leaderLatch = new leader.LeaderLatch(
      curator,
      path,
      self.path.toSerializationFormat,
      closeMode match {
        case CloseMode.SILENT         => leader.LeaderLatch.CloseMode.SILENT
        case CloseMode.NOTIFY_LEADER  => leader.LeaderLatch.CloseMode.NOTIFY_LEADER
      }
    )

    private val leaderPathCase  = LeaderPath(path)
    private val isLeaderCase    = IsLeader(path)
    private val notLeaderCase   = NotLeader(path)

    private val leaderLatchListener = new leader.LeaderLatchListener {
      override def isLeader(): Unit = {
        subscribers.foreach(_.tell(isLeaderCase, Actor.noSender))
      }
      override def notLeader(): Unit = {
        subscribers.foreach(_.tell(notLeaderCase, Actor.noSender))
      }
    }

    override def preStart(): Unit = {
      leaderLatch.addListener(leaderLatchListener)
      checkNoSubscribers()
    }

    def receive = {
      case Subscribe =>
        addSubscriber(sender())
      case UnSubscribe =>
        removeSubscriber(sender())
      case MayBeJoinFor(ref) =>
        if (subscribers.contains(ref)) {
          if (leaderLatch.getState == leader.LeaderLatch.State.LATENT)
            leaderLatch.start()
          ref.tell(leaderPathCase, Actor.noSender)
          ref.tell(if (leaderLatch.hasLeadership) isLeaderCase else notLeaderCase, Actor.noSender)
          Curator(context.system).tell(Curator.Reaper.AddPath(path, Some(Curator.buildPath(LeaderLatch.pathPrefix, "")), Reaper.Mode.REAP_UNTIL_GONE), context.parent)
        } else {
          log.error(new IllegalStateException(), "{} seems like terminated and can not be joined to LeaderLatch", ref)
        }
      case NoSubscribersCountdown =>
        if (subscribers.isEmpty){
          log.debug("Stopping due idle {} without subscribers", idleTimeout)
          context.stop(self)
        }
      case Terminated(ref) =>
        removeSubscriber(ref, silent = true)
    }

    override def postStop(): Unit = {
      noSubscribersTimer.cancel()
      leaderLatch.removeListener(leaderLatchListener)
      if (leaderLatch.getState == leader.LeaderLatch.State.STARTED)
        leaderLatch.close()
    }

    private def addSubscriber(ref: ActorRef): Unit = {
      if (!subscribers.contains(ref)) {
        context.watch(ref)
        subscribers += ref
      } else {
        log.warning("Already joined: {}", ref)
      }
      context.system.scheduler.scheduleOnce(20.milliseconds, self, MayBeJoinFor(ref))
    }

    private def removeSubscriber(ref: ActorRef, silent: Boolean = false): Unit = {
      if (subscribers.contains(ref)) {
        subscribers -= ref
        context.unwatch(ref)
        if (!silent)
          ref.tell(notLeaderCase, Actor.noSender)
        checkNoSubscribers()
      } else {
        log.warning("Not joined before: {}", ref)
      }
    }

    private def checkNoSubscribers(): Unit = {
      if (subscribers.isEmpty)
        if (noSubscribersTimer != null && !noSubscribersTimer.isCancelled) noSubscribersTimer.cancel()
        noSubscribersTimer = context.system.scheduler.scheduleOnce(idleTimeout, self, NoSubscribersCountdown)
    }
  }

  private[LeaderLatch] object Watcher {
    private[LeaderLatch] case object Subscribe
    private[LeaderLatch] case object UnSubscribe
    private[Watcher] case object NoSubscribersCountdown
    private[Watcher] case class MayBeJoinFor(ref: ActorRef)

    private[LeaderLatch] def props(curator: CuratorFramework, path: String, closeMode: CloseMode.Value, idleTimeout: FiniteDuration): Props =
      Props(classOf[Watcher], curator, path, closeMode, idleTimeout)
  }
}
