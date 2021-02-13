package com.bdesigns.akka.rest

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.bdesigns.akka.actors.RedisActor.RedisFn
import com.bdesigns.akka.actors.RedisSubscriber._
import com.bdesigns.akka.actors._
import com.bdesigns.akka.json.Json4sFormat
import com.bdesigns.akka.utils.RedisConnector
import com.redis._
import org.slf4j.Logger

import java.net.URI
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait RedisKeyEvents extends StreamingActor {
  val redisUri: URI = RedisConnector.getConnectionUri
  val actorSystem: ActorSystem[Nothing]
  val redisClient: RedisClient = new RedisClient(redisUri)

  val redisSubscriberActor: ActorRef[Msg] = actorSystem.systemActorOf(RedisSubscriber(redisUri), "redis-sub")
  redisSubscriberActor ! Register(callback)
  redisSubscriberActor ! Subscribe(Array(EVENT_SET, EVENT_EXPIRED))

  def callback(pubsub: PubSubMessage): Unit = pubsub match {
    case S(channel, no) => println("subscribed to " + channel + " and count = " + no)
    case U(channel, no) => println("unsubscribed from " + channel + " and count = " + no)
    case M(EVENT_EXPIRED, "name") =>
      streamingActor ! StreamingEventSourceActor.UserInfoChange("---", online = false)
    case M(EVENT_SET, keyname) =>
      val username: String = redisClient.get(keyname).getOrElse("---")
      streamingActor ! StreamingEventSourceActor.UserInfoChange(username, online = true)
    case _ => println(pubsub)
  }
}

trait TestService extends Json4sFormat with RedisKeyEvents {
  val logger: Logger
  implicit val actorSystem: ActorSystem[IoTSupervisor.IoTCommand]
  val redisUri: URI
  val redisClient: RedisClient
//  val redisActor: ActorRef[RedisFn] = actorSystem.systemActorOf(RedisActor(redisUri), "redis-actor")
  implicit val timeout: Timeout

  val dbRestarts: Behavior[RedisFn] = Behaviors
    .supervise(Behaviors.supervise(RedisActor(redisUri)).onFailure[RuntimeException](SupervisorStrategy.restart))
    .onFailure[IllegalStateException](SupervisorStrategy.restart)

  val redisActorFuture: Future[ActorRef[RedisFn]] = actorSystem.ask(ref => IoTSupervisor.IoTSpawn(dbRestarts, "redis-actor", ref))
//  val redisActorFuture: Future[ActorRef[RedisFn]] = actorSystem.ask(ref => IotSupervisor.IoTSpawn(RedisActor(redisUri), "redis-actor", ref))
  val redisActor: ActorRef[RedisFn] = Await.result(redisActorFuture, 3.seconds)

  println(s"redisActor $redisActor")
  println(s"subActor $redisSubscriberActor")
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
      import scala.concurrent.duration._

      redisActor ! RedisActor.Set("name", "okiedokie", Some(20.seconds))
      complete(s"Uri ${redisUri.toString}")
    },
    path ("actors") {
      val actors = s"""
        |actorSystem $actorSystem
        |streamingActor $streamingActor
        |redisActor $redisActor
        |subActor $redisSubscriberActor
      """.stripMargin
      complete(actors)
    },
    path("fetch") {
      pathEnd {
        val responseFuture: Future[RedisActor.Reply] = redisActor.ask(ref => RedisActor.Get("name", ref))

        onComplete(responseFuture) {
          case Success(RedisActor.RedisResponse(msg)) => complete {
            s"""received ${msg.getOrElse("-----")}"""
          }
          case Failure(ex) => complete(s"Yuk: ${ex.getMessage}")
        }
      }
    }
  )
}
