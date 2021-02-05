package com.bdesigns.akka.rest

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{DelayOverflowStrategy, OverflowStrategy}
import com.bdesigns.akka.actors.StreamingEventSourceActor
import com.bdesigns.akka.json.Json4sFormat

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait BasicService extends Json4sFormat {

  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
  import com.bdesigns.akka.actors.{Subscribe, Unsubscribe}

  implicit val actorSystem: ActorSystem
  implicit val executionContext: ExecutionContextExecutor
  val logger: LoggingAdapter

  def queue(): (SourceQueueWithComplete[String], Source[ServerSentEvent, NotUsed]) = Source.queue[String](Int.MaxValue, OverflowStrategy.backpressure)
    .delay(1.seconds, DelayOverflowStrategy.backpressure)
    .map(message => ServerSentEvent(message, Some("myEvent")))
    .keepAlive(1.second, () => ServerSentEvent.heartbeat)
    .toMat(BroadcastHub.sink[ServerSentEvent])(Keep.both)
    .run()

//  lazy val streamingActor: ActorRef = actorSystem.actorOf(StreamingEventSourceActor.props(sourceQueue), name = StreamingEventSourceActor.name)
  lazy val streamingActor: ActorRef = actorSystem.actorOf(StreamingEventSourceActor.props(), name = StreamingEventSourceActor.name)

  lazy val basicRoute: Route =
    path("events") {
      concat(
        get {
          cookie("theCookie") { sessionCookie =>
            complete {
              val (sourceQueue, eventsSource) = queue()
              val key = sessionCookie.value
              streamingActor ! Subscribe(key, sourceQueue)
              logger.warning(s"subscribe $key")
              val ev = eventsSource
                .watchTermination() { (m, f) =>
                  f.onComplete(r => {
                    logger.warning(s"Unsubscribe $key")
                    streamingActor ! Unsubscribe(key)
                    logger.warning(r.toString)
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
