package com.cleawing.akka.playground

import akka.actor._
import akka.dispatch._
import com.typesafe.config.Config

case class SafeUnboundedMailbox() extends MailboxType with ProducesMessageQueue[SafeUnboundedMailbox.MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = {
    new SafeUnboundedMailbox.MessageQueue
  }
}

object SafeUnboundedMailbox {
  class MessageQueue extends UnboundedMailbox.MessageQueue {
    override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
      handle.message match {
        case _: PoisonPill | Kill | Identify => // suppress not safe messages
        case _ => super.enqueue(receiver, handle)
      }
    }
    override def dequeue(): Envelope = super.poll()
  }
}
