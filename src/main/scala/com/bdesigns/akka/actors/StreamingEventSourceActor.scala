package com.bdesigns.akka.actors

import java.util.Calendar
import _root_.akka.actor.{Actor, Cancellable, Props}
import _root_.akka.stream.scaladsl.SourceQueueWithComplete
import com.bdesigns.akka.json.Json4sFormat
import scala.concurrent.duration._

sealed trait SSEActor
case object StartClock extends SSEActor
case object StopClock extends SSEActor
case class Subscribe(id: String, source: SourceQueueWithComplete[String]) extends SSEActor
case class Unsubscribe(id: String) extends SSEActor

object StreamingEventSourceActor {
  def name = "StreamingEventSourceActor"
  def props(): Props = Props(new StreamingEventSourceActor)
  case class UpdateDashboard(data: String)
}

class StreamingEventSourceActor()
  extends Actor
    with Json4sFormat {

  private var cache = Map.empty[String, SourceQueueWithComplete[String]]
  implicit val ec = context.system.dispatcher
  private var cancellableO: Option[Cancellable] = None

  override def receive: Receive = {

    case Subscribe(id, source) => {
      if (cache.isEmpty) {
        self ! StartClock
      }
      cache = cache + (id -> source)
      println(s"Add $id, length ${cache.size}")
    }
    case Unsubscribe(id) => {
      cache = cache - id
      println(s"Remove $id, length ${cache.size}")
    }

    case StartClock => {

      //send periodic date/time to all browsers
      val dt = Calendar.getInstance().getTime()
      val response = s"""{"msg":"${dt}"}"""
      println(s"sending $response")
      cancellableO = Some(context.system.scheduler.scheduleOnce(5.second) {
        self ! StartClock
      })
      cache.values.foreach(source => {
        println("offering response")
        source.offer(response)
      })
    }

    case StopClock => {
      cancellableO match {
        case Some(cancellable) =>
          cancellable.cancel()
        case None =>
      }
    }

//    case StreamingEventSourceActor.UpdateDashboard(msg) => {
//      // send broadcase to all connect browsers. Triggered by PUT method in Rest end point PUT events
//      val testMessage =
//        s"""{"msg":"$msg"}
//          |""".stripMargin
//      source.offer(testMessage)
//    }
  }
}