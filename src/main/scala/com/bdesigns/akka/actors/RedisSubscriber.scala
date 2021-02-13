package com.bdesigns.akka.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.redis.{PubSubMessage, RedisClient}

import java.net.URI

object RedisSubscriber {
  val EVENT_SET = "__keyevent@0__:set"
  val EVENT_DEL = "__keyevent@0__:del"
  val EVENT_EXPIRED = "__keyevent@0__:expired"
  val EVENT_EVICTED = "__keyevent@0__:evicted"

  sealed trait Msg
  case class Subscribe(channels: Array[String]) extends Msg
  case class Register(callback: PubSubMessage => Any) extends Msg
  case class Unsubscribe(channels: Array[String]) extends Msg
  case object UnsubscribeAll extends Msg
  case class Publish(channel: String, msg: String) extends Msg

  def apply(redisUrl: URI): Behavior[Msg] = initial(new RedisClient(redisUrl))

  private def initial(redisClient: RedisClient): Behavior[Msg] = {
    Behaviors.receive { (_, message) =>
      message match {
        case Register(cb) =>
          process(redisClient, cb)
        case _ =>
          Behaviors.unhandled
      }
    }
  }

  private def process(redisClient: RedisClient, callback: PubSubMessage => Any): Behavior[Msg] = {
    Behaviors.receive { (_, message) =>
      message match {
        case Subscribe(channels) =>
          redisClient.subscribe(channels.head, channels.tail:_*)(callback)
          Behaviors.same
        case Unsubscribe(channels) =>
          redisClient.unsubscribe(channels.head, channels.tail:_*)
          Behaviors.same
        case UnsubscribeAll =>
          redisClient.unsubscribe()
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }
    }
  }
}
