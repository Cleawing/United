package com.cleawing.consul

import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

object ConsulExtension
  extends ExtensionId[ConsulExtensionImpl]
  with ExtensionIdProvider {

  override def lookup() = ConsulExtension

  override def createExtension(system: ExtendedActorSystem) = new ConsulExtensionImpl(system)
}

class ConsulExtensionImpl(system: ExtendedActorSystem) extends Extension {
  private val ref = system.systemActorOf(Consul.props(), "consul")
}
