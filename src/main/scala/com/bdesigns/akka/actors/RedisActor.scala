package com.bdesigns.akka.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import com.bdesigns.akka.actors.IoTSupervisor.IoTCommand
import com.redis.RedisClient

import java.net.URI
import scala.concurrent.duration.Duration

object RedisActor {
  sealed trait Reply
  case class RedisResponse(item: Option[String]) extends Reply

  sealed trait RedisFn
  case class Get(key: String, replyTo: ActorRef[Reply]) extends RedisFn
  case class Set(key: String, value: String, expires: Option[Int]) extends RedisFn
  case object GracefulShutdown extends RedisFn

  def apply(redisUrl: URI): Behavior[RedisFn] = initial(new RedisClient(redisUrl))

  private def initial(redisClient: RedisClient): Behavior[RedisFn] = {
    val res = Behaviors
      .receive[RedisFn] {(context, message) =>
        message match {
          case RedisActor.Get(key, replyTo) =>
            replyTo ! RedisResponse(redisClient.get(key))
            Behaviors.same
          case RedisActor.Set(key, value, expiresMaybe) =>
            expiresMaybe match {
              case Some(duration) =>
                redisClient.set(key, value, expire = Duration(duration, "seconds"))
              case _ =>
                redisClient.set(key, value)
            }
            Behaviors.same
          case GracefulShutdown =>
            context.log.info("RedisActor Initiating graceful shutdown...")
            // Here it can perform graceful stop (possibly asynchronous) and when completed
            // return `Behaviors.stopped` here or after receiving another message.
            Behaviors.stopped
        }
      }
      .receiveSignal {
        case (context, PostStop) =>
          context.log.info("RedisActor stopped")
          Behaviors.same
      }
    res
  }
}
