package com.cleawing.akka.etcd

import java.net.URI

import akka.actor.{Props, Actor}
import mousio.client.retry.RetryPolicy
import mousio.etcd4j.EtcdClient

private[etcd] class EtcdGuardian(peers: Seq[String], retryPolicy: RetryPolicy) extends Actor {
  private var etcd: EtcdClient = _

  override def preStart(): Unit = {
    etcd = new EtcdClient(peers.map(URI.create): _*)
    etcd.setRetryHandler(retryPolicy)
  }

  def receive = Actor.ignoringBehavior

  override def postStop(): Unit = {
    etcd.close()
  }
}

private[etcd] object EtcdGuardian {
  def props(peers: Seq[String], retryPolicy: RetryPolicy) : Props = Props(classOf[EtcdGuardian], peers, retryPolicy)
}
