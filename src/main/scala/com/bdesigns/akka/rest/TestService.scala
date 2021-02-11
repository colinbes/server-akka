package com.bdesigns.akka.rest

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.bdesigns.akka.actors.StreamingEventSourceActor
import com.bdesigns.akka.actors.StreamingEventSourceActor.SSEActor
import com.bdesigns.akka.json.Json4sFormat
import com.bdesigns.akka.utils.RedisConnector
import com.redis._
import org.slf4j.Logger

import java.net.URI
import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions

trait TestService extends Json4sFormat {
  val logger: Logger
  val redisUri: URI = RedisConnector.getConnectionUri
  val streamingActor: ActorRef[SSEActor]
  implicit val executionContext: ExecutionContextExecutor

  val EVENT_SET = "__keyevent@0__:set"
  val EVENT_DEL = "__keyevent@0__:del"
  val EVENT_EXPIRED = "__keyevent@0__:expired"
  val EVENT_EVICTED = "__keyevent@0__:evicted"

  val redisSubClient = new RedisClient(redisUri)
  println(s"REDIS URL ${redisUri.toString}")
  redisSubClient.subscribe(EVENT_SET, EVENT_EXPIRED)(callback)

  val redisClient = new RedisClient(redisUri)

  def callback(pubsub: PubSubMessage): Unit = pubsub match {
    case S(channel, no) => println("subscribed to " + channel + " and count = " + no)
    case U(channel, no) => println("unsubscribed from " + channel + " and count = " + no)
    case M(EVENT_EXPIRED, keyname) => {
      streamingActor ! StreamingEventSourceActor.UserInfoChange("---", false)
    }
    case M(EVENT_SET, keyname) => {
      val username: String = redisClient.get("name").getOrElse("---")
      streamingActor ! StreamingEventSourceActor.UserInfoChange(username, true)
    }
    case _ => println(pubsub)
  }

  val testRoute: Route = concat (
    path("test") {
      pathEnd {
          extractClientIP { clientIP =>
            complete {
              s"IP: ${clientIP.toString}"
          }
        }
      }
    },
    path("info") {
      complete(s"Uri ${redisUri.toString}")
    },
    path("fetch") {
      pathEnd {
        val maybe: Option[String] = redisClient.get("name")
        maybe match {
          case Some(value) => complete(value)
          case None => complete("Not found")
        }
      }
    }
  )
}
