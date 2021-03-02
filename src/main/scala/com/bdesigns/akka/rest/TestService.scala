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
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.slf4j.Logger

import java.net.URI
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

case class ActorListItem(name: String, ref: String)

trait RedisKeyEvents extends StreamingActor {
  implicit val actorSystem: ActorSystem[IoTSupervisor.IoTCommand]
  implicit val executionContext: ExecutionContextExecutor
  implicit val timeout: Timeout
  val redisUri: URI = RedisConnector.getConnectionUri
  val logger: Logger

  val redisClient: RedisClient = {
    logger.info(s"Connecting to redis at $redisUri")
    new RedisClient(redisUri)
  }

  val redisSubscriberFuture: Future[ActorRef[Msg]] = actorSystem.ask(ref => IoTSupervisor.IoTSpawn(RedisSubscriber(redisUri), "redis-sub", ref))

  for {
    redisSubscriberActor <- redisSubscriberFuture
  } {
    redisSubscriberActor ! Register(callback)
    redisSubscriberActor ! Subscribe(Array(EVENT_SET, EVENT_EXPIRED))
  }


  def callback(pubsub: PubSubMessage): Unit = pubsub match {
    case S(channel, no) => logger.info("subscribed to " + channel + " and count = " + no)
    case U(channel, no) => logger.info("unsubscribed from " + channel + " and count = " + no)
    case M(EVENT_EXPIRED, "name") =>
      streamingActor ! StreamingEventSourceActor.UserInfoChange("---", online = false)
    case M(EVENT_SET, keyname) =>
      val username: String = redisClient.get(keyname).getOrElse("---")
      streamingActor ! StreamingEventSourceActor.UserInfoChange(username, online = true)
    case _ => logger.info(s"Unknown callback pubsub ${pubsub.toString}")
  }
}

trait TestService extends Json4sFormat
  with RedisKeyEvents
  with Json4sSupport {
  implicit val actorSystem: ActorSystem[IoTSupervisor.IoTCommand]
  implicit val executionContext: ExecutionContextExecutor
  implicit val timeout: Timeout
  val logger: Logger
  val redisUri: URI
  val redisClient: RedisClient

  val dbRestarts: Behavior[RedisFn] = Behaviors
    .supervise(Behaviors.supervise(RedisActor(redisUri)).onFailure[RuntimeException](SupervisorStrategy.restart))
    .onFailure[IllegalStateException](SupervisorStrategy.restart)

  val redisActorFuture: Future[ActorRef[RedisFn]] = actorSystem.ask(ref => IoTSupervisor.IoTSpawn(dbRestarts, "redis-actor", ref))
  val testRoute: Route = concat (
    path("set") {
      post {
        entity(as[RedisActor.Set]) { dataSet =>
          val resFuture: Future[Unit] = redisActorFuture.map(redisActor =>
            redisActor ! RedisActor.Set(dataSet.key, dataSet.value, dataSet.expires)
          )
          onComplete(resFuture) {
            case Success(_)  => complete("OK")
            case Failure(ex) => complete(s" Exception ${ex.getMessage}")
          }
        }
      }
    },
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
      get {
        val resFuture: Future[Unit] = redisActorFuture.map(redisActor1 => redisActor1 ! RedisActor.Set("name", "okiedokie", Some(20)))
        onComplete(resFuture) {
          case Success(_)  => complete(s"Uri ${redisUri.toString}")
          case Failure(ex) => complete(s" Exception ${ex.getMessage}")
        }
      }
    },
    path ("actors") {
      val actorsFuture = for {
        redisActor <- redisActorFuture
        redisSubscriberActor <- redisSubscriberFuture
      } yield {
        List(
          ActorListItem("actorSystem", actorSystem.path.toStringWithoutAddress),
          ActorListItem("streamingActor", streamingActor.path.toStringWithoutAddress),
          ActorListItem("redisActor", redisActor.path.toStringWithoutAddress),
          ActorListItem("subActor", redisSubscriberActor.path.toStringWithoutAddress)
        )
      }
      onComplete(actorsFuture) {
        case Success(actors) => complete(actors)
        case Failure(ex)     => complete(ex.getMessage)
      }
    },
    path("fetch") {
      pathEnd {
        val responseFuture: Future[RedisActor.Reply] = redisActorFuture.flatMap(redisActor => redisActor.ask(ref => RedisActor.Get("name", ref)))

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
