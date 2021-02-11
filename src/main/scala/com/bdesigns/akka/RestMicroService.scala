package com.bdesigns.akka

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.util.Timeout
import com.bdesigns.akka.actors.{IotSupervisor, StreamingEventSourceActor}
import com.bdesigns.akka.actors.StreamingEventSourceActor.SSEActor
import com.redis.{M, PubSubMessage, RedisClient, S, U}
import org.slf4j.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

trait MyTrait {
  def getCookiePath: String
  def getRootContext: String
}

object RestMicroService extends App
  with RestInterface
  with RootContext
  with CORSHandler {

//  val etagTimeToLive = 5.minutes

/*  val cookiePath = s"/$getRootContext"
  val cookieLifetime = 30*60000L
  val cookieName = "ABTReportCookie"

  def getCookiePath = cookiePath
*/

  implicit val timeout: Timeout = Timeout(4.seconds)
  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](IotSupervisor(), "bdesigns-akka")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext
  lazy val streamingActor: ActorRef[SSEActor] = actorSystem.systemActorOf(StreamingEventSourceActor(), "eventsource")

  val logger: Logger = actorSystem.log

  //  val api = routes
  val api = DebuggingDirectives.logRequest("AkkaRest", Logging.WarningLevel)(routes)
  val serverBinding: Future[Http.ServerBinding] = Http().newServerAt("0.0.0.0", 8082).bind(api)

  serverBinding.onComplete {
    case Success(bound) =>
      logger.info(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      logger.warn(s"Server could not start! ${e.getMessage}")
      actorSystem.terminate()
  }
  // StdIn.readLine()
  // system.terminate()
  Await.result(actorSystem.whenTerminated, Duration.Inf)
}
