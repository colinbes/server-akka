package com.bdesigns.akka

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.util.Timeout
import com.bdesigns.akka.RestMicroService.actorSystem
import com.bdesigns.akka.actors.StreamingEventSourceActor.SSEActor
import com.bdesigns.akka.actors.{IoTSupervisor, StreamingEventSourceActor}
import org.slf4j.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

trait MyTrait {
  def getCookiePath: String
  def getRootContext: String
}

trait IoTActor {
  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val actorSystem: ActorSystem[IoTSupervisor.IoTCommand] = ActorSystem[IoTSupervisor.IoTCommand](IoTSupervisor(), "bdesigns-akka")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext
  val logger: Logger = actorSystem.log
}

trait StreamingActorImpl {
  implicit val timeout: Timeout
  val responseFuture: Future[ActorRef[SSEActor]] = actorSystem.ask(ref => IoTSupervisor.IoTSpawn(StreamingEventSourceActor(), "source-event", ref))
  val streamingActor: ActorRef[SSEActor] = Await.result(responseFuture, 3.seconds)
}

object RestMicroService extends App
  with IoTActor with StreamingActorImpl
  with RestInterface
  with RootContext {

  val CookieName = "abt-cookie-api"
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

  logger.info(s"actorSystem $actorSystem")
  logger.info(s"streamingActor $streamingActor")
  // StdIn.readLine()
  // system.terminate()
  Await.result(actorSystem.whenTerminated, Duration.Inf)
}
