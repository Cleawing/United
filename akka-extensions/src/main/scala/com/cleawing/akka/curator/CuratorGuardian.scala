package com.cleawing.akka.curator

import akka.actor.{ActorRef, Props, Actor}
import org.apache.curator.RetryPolicy
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.framework.CuratorFrameworkFactory
import scala.collection.concurrent.TrieMap

private[curator] class CuratorGuardian(ensembleProvider: EnsembleProvider, retryPolicy: RetryPolicy, namespace: String) extends Actor {
  private val curator = CuratorFrameworkFactory.builder()
    .ensembleProvider(ensembleProvider)
    .retryPolicy(retryPolicy)
    .namespace(namespace)
    .build()

  private var leaderLatch: ActorRef = _
  private var reaper: ActorRef = _

  override def preStart(): Unit = {
    curator.start()
    leaderLatch = context.actorOf(recipes.LeaderLatch.props(curator), "leader-latch")
    reaper      = context.actorOf(recipes.Reaper.props(curator, Curator(context.system).settings.reaperThreshold), "reaper")
  }

  def receive = {
    case request: Curator.LeaderLatch.Request => leaderLatch forward request
    case request: Curator.Reaper.Request => reaper forward request
  }

  override def postStop(): Unit = {
    curator.close()
  }
}

private[curator] object CuratorGuardian {
  def props(ensembleProvider: EnsembleProvider, retryPolicy: RetryPolicy, namespace: String) : Props =
    Props(classOf[CuratorGuardian], ensembleProvider, retryPolicy, namespace)

  type RecipeActors = TrieMap[String, ActorRef]
}
