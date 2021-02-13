package com.bdesigns.akka.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.redis.RedisClient

import java.net.URI
import scala.concurrent.duration.Duration

object RedisActor {
  sealed trait Reply
  case class RedisResponse(item: Option[String]) extends Reply

  sealed trait RedisFn
  case class Get(key: String, replyTo: ActorRef[Reply]) extends RedisFn
  case class Set(key: String, value: String, expires: Option[Duration] = None) extends RedisFn

  def apply(redisUrl: URI): Behavior[RedisFn] = initial(new RedisClient(redisUrl))

  private def initial(redisClient: RedisClient): Behavior[RedisFn] = {
    println("#### Staring REDIS ACTOR ###")
    Behaviors.receive { (_, message) =>
      message match {
        case RedisActor.Get(key, replyTo) =>
          replyTo ! RedisResponse(redisClient.get(key))
          Behaviors.same
        case RedisActor.Set(key, value, expiresMaybe) =>
          expiresMaybe match {
            case Some(duration) => redisClient.set(key, value, expire = duration)
            case _ => redisClient.set(key, value)
          }
          Behaviors.same
      }
    }
  }
}
