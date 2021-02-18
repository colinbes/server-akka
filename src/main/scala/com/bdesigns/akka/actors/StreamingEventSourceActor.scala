package com.bdesigns.akka.actors

import java.util.Calendar
import _root_.akka.actor.{Actor, ActorLogging, Cancellable, Props}
import _root_.akka.stream.scaladsl.SourceQueueWithComplete
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.bdesigns.akka.actors.StreamingEventSourceActor.SSEActor
import com.bdesigns.akka.json.Json4sFormat._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import org.json4s.jackson.Serialization.write

trait StreamingActor {
  val streamingActor: ActorRef[SSEActor]
}

object StreamingEventSourceActor {
  sealed trait StreamedResponse[T] {
    def event: String
    def data: T
  }
  case class ClockStreamedResponse[String](event: String = "date", value: String)
  case class UserInfoStreamedResponse[UserInfoChange](event: String = "userchange", value: UserInfoChange)

  sealed trait SSEActor
  case object StartClock extends SSEActor
  case object StopClock extends SSEActor
  case class Subscribe(id: String, source: SourceQueueWithComplete[String]) extends SSEActor
  case class Unsubscribe(id: String) extends SSEActor
  case class UserInfoChange(name: String, online: Boolean) extends SSEActor

  def apply(): Behavior[SSEActor] = initial(Map.empty[String, SourceQueueWithComplete[String]])

  private def initial(cache: Map[String, SourceQueueWithComplete[String]]): Behavior[SSEActor] = {
    Behaviors.receive { (context, message) =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContextExecutor = system.executionContext

      message match {
        case Subscribe(id, source) => {
          context.log.info(s"Add $id, length ${cache.size}")
          running(cache + (id -> source), context.system.scheduler.scheduleOnce(5.second, {() => context.self ! StartClock}))
        }
        case _ => Behaviors.unhandled
      }
    }
  }

  private def running(cache: Map[String, SourceQueueWithComplete[String]], cancellable: Cancellable): Behavior[SSEActor] = {
    Behaviors.receive {(context, message) =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContextExecutor = system.executionContext

      message match {
        case Subscribe(id, source) => {
          context.log.info(s"Add $id, length ${cache.size}")
          running(cache + (id -> source), cancellable)
        }
        case Unsubscribe(id) => {
          val cache1 = cache - id
          if (cache1.isEmpty) {
            context.self ! StopClock
          }
          context.log.info(s"Remove $id, length ${cache.size}")
          running(cache1 - id, cancellable)
        }
        case info@UserInfoChange(name, online) => {
          context.log.info(s"UserInfoChange $name, $online")
          val response = write(UserInfoStreamedResponse(value = info))
          cache.values.foreach(source => {
            source.offer(response)
          })
          Behaviors.same
        }
        case StartClock => {
          //send periodic date/time to all browsers
          val dt = Calendar.getInstance().getTime()
          val response = write(ClockStreamedResponse(value = dt.toString))
          context.log.info(s"sending $response")
          cache.values.foreach(source => {
            context.log.info("offering response")
            source.offer(response)
          })
          running(cache, context.system.scheduler.scheduleOnce(5.second, {() => context.self ! StartClock}))
        }

        case StopClock => {
          val result = cancellable.cancel()
          context.log.info(s"stopping clock $result")
          initial(Map.empty[String, SourceQueueWithComplete[String]])
        }
      }
    }
  }
}