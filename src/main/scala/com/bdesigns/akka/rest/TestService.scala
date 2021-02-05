package com.bdesigns.akka.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.bdesigns.akka.json.Json4sFormat
import com.redis._

import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions

trait TestService extends Json4sFormat {
  val redisPort = 12345 //move to config
  implicit val executionContext: ExecutionContextExecutor
  val EVENT_SET = "__keyevent@0__:set"
  val EVENT_DEL = "__keyevent@0__:del"
  val EVENT_EXPIRED = "__keyevent@0__:expired"
  val EVENT_EVICTED = "__keyevent@0__:evicted"


  def callback(pubsub: PubSubMessage): Unit = pubsub match {
    case S(channel, no) => println("subscribed to " + channel + " and count = " + no)
    case U(channel, no) => println("unsubscribed from " + channel + " and count = " + no)
    case M(EVENT_EXPIRED, keyname) => println(s"exp $keyname")
    case M(EVENT_SET, keyname) => println(s"set $keyname")
    case _ => println(pubsub)
  }

  val redisSubClient = new RedisClient("localhost", redisPort)
  redisSubClient.subscribe(EVENT_SET, EVENT_EXPIRED)(callback)

  val redisClient = new RedisClient("localhost", redisPort)

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
