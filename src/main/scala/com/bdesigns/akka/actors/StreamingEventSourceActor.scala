package com.bdesigns.akka.actors

import java.util.Calendar
import _root_.akka.actor.{Actor, Cancellable, Props}
import _root_.akka.stream.scaladsl.SourceQueueWithComplete
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.bdesigns.akka.json.Json4sFormat._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import org.json4s.jackson.Serialization.write

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

  def apply(): Behavior[SSEActor] = {
    var cache = Map.empty[String, SourceQueueWithComplete[String]]
    var cancellableO: Option[Cancellable] = None

    Behaviors.receive { (context, message) =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContextExecutor = system.executionContext

      message match {
        case Subscribe(id, source) => {
          if (cache.isEmpty) {
            context.self ! StartClock
          }
          cache = cache + (id -> source)
          println(s"Add $id, length ${cache.size}")
          Behaviors.same
        }
        case Unsubscribe(id) => {
          cache = cache - id
          if (cache.isEmpty) {
            context.self ! StopClock
          }
          println(s"Remove $id, length ${cache.size}")
          Behaviors.same
        }
        case info@UserInfoChange(name, online) => {
          println(s"UserInfoChange $name, $online")
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
          println(s"sending $response")
          cancellableO = Some(context.system.scheduler.scheduleOnce(5.second, {() => context.self ! StartClock}))
          cache.values.foreach(source => {
            println("offering response")
            source.offer(response)
          })
          Behaviors.same
        }

        case StopClock => {
          cancellableO match {
            case Some(cancellable) =>
              if (cancellable.cancel()) cancellableO = None
            case None =>
          }
          Behaviors.same
        }
      }
    }
  }
}