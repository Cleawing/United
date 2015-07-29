package com.cleawing.consul

import akka.actor.{Props, Actor}

class Consul extends Actor {
  def receive = Actor.emptyBehavior
}

object Consul {
  def props() : Props = Props[Consul]
}
