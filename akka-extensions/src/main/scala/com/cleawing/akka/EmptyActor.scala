package com.cleawing.akka

import akka.actor.{Props, Actor}

class EmptyActor extends Actor {
  def receive = {
    case msg => println(msg)
  }
}

object EmptyActor {
  def props(): Props = Props[EmptyActor]
}
