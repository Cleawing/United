package com.cleawing.akka.curator

import akka.actor.{Props, Actor}
import org.apache.curator.RetryPolicy
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.framework.CuratorFrameworkFactory

private[curator] class CuratorGuardian(ensembleProvider: EnsembleProvider, retryPolicy: RetryPolicy, namespace: String) extends Actor {
  private val curator = CuratorFrameworkFactory.builder()
    .ensembleProvider(ensembleProvider)
    .retryPolicy(retryPolicy)
    .namespace(namespace)
    .build()

  override def preStart(): Unit = {
    curator.start()
  }

  def receive = Actor.emptyBehavior

  override def postStop(): Unit = {
    curator.close()
  }
}

private[curator] object CuratorGuardian {
  def props(ensembleProvider: EnsembleProvider, retryPolicy: RetryPolicy, namespace: String) : Props =
    Props(classOf[CuratorGuardian], ensembleProvider, retryPolicy, namespace)
}
