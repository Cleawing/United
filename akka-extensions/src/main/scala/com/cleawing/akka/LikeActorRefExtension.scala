package com.cleawing.akka

import akka.actor._
import akka.event.LoggingAdapter

trait LikeActorRefExtension extends Extension {
  protected def target: ActorRef
  protected def system: ExtendedActorSystem
  private var _log: LoggingAdapter = _

  def log: LoggingAdapter = {
    if (_log eq null)
      _log = akka.event.Logging(system, this.getClass)
    _log
  }

  final def tell(msg: Any, sender: ActorRef): Boolean = this.!(msg)(sender)

  final def !(message: Any)(implicit sender: ActorRef = Actor.noSender): Boolean = (message, sender) match {
    case (_, Actor.noSender) =>
      log.warning("Actor.noSender is not applicable as sender")
      false
    case (msg: PossiblyHarmful, _) =>
      log.warning("PossiblyHarmful message {} is not applicable", msg)
      false
    case _ => target.tell(message, sender); true
  }

  final def forward(message: Any)(implicit context: ActorContext): Boolean = tell(message, context.sender())
}
