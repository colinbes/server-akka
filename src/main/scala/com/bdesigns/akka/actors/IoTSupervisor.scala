package com.bdesigns.akka.actors

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import com.bdesigns.akka.actors.IoTSupervisor.{IoTCommand, IoTSpawn}

object IoTSupervisor {
  sealed trait IoTCommand
  final case class IoTSpawn[U](behavior: Behavior[U], name: String, replyTo: ActorRef[ActorRef[U]]) extends IoTCommand
  def apply(): Behavior[IoTCommand] =
    Behaviors.setup[IoTCommand](context => new IoTSupervisor(context))
}

class IoTSupervisor(context: ActorContext[IoTCommand]) extends AbstractBehavior[IoTCommand](context) {
  context.log.info("IoT Application started")
  override def onMessage(msg: IoTCommand): Behavior[IoTCommand] = msg match {
    case IoTSpawn(behavior, name, replyTo) =>
      val actor = context.spawn(behavior, name)
      replyTo ! actor
      Behaviors.same
    case _ => Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[IoTCommand]] = {
    case PostStop =>
      context.log.info("IoT Application stopped")
      this
  }
}