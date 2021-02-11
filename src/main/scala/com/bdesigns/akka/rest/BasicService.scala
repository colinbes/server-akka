package com.bdesigns.akka.rest

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{DelayOverflowStrategy, OverflowStrategy}
import com.bdesigns.akka.actors.StreamingEventSourceActor
import com.bdesigns.akka.actors.StreamingEventSourceActor._
import com.bdesigns.akka.json.Json4sFormat
import org.slf4j.Logger

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait BasicService extends Json4sFormat {

  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
  implicit val actorSystem: ActorSystem[Nothing]
  implicit val executionContext: ExecutionContextExecutor
  val logger: Logger

  val streamingActor: ActorRef[SSEActor]

  def queue(): (SourceQueueWithComplete[String], Source[ServerSentEvent, NotUsed]) = Source.queue[String](Int.MaxValue, OverflowStrategy.backpressure)
    .delay(1.seconds, DelayOverflowStrategy.backpressure)
    .map(message => ServerSentEvent(message, Some("myEvent")))
    .keepAlive(1.second, () => ServerSentEvent.heartbeat)
    .toMat(BroadcastHub.sink[ServerSentEvent])(Keep.both)
    .run()

//  val consumerActor = userActor.systemActorOf(ConsumerActor(consumerFn), "pulsar-consumer")
  lazy val basicRoute: Route =
    path("events") {
      concat(
        get {
          cookie("theCookie") { sessionCookie =>
            complete {
              val (sourceQueue, eventsSource) = queue()
              val key = sessionCookie.value
              streamingActor ! StreamingEventSourceActor.Subscribe(key, sourceQueue)
              logger.warn(s"subscribe $key")
              val ev = eventsSource
                .watchTermination() { (m, f) =>
                  f.onComplete(r => {
                    logger.warn(s"Unsubscribe $key")
                    streamingActor ! StreamingEventSourceActor.Unsubscribe(key)
                    logger.warn(r.toString)
                  })
                  m
                }
              ev
            }
          }
        }
      )
    }
}
