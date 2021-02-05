package com.bdesigns.akka

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

trait RestInterface extends RestServices
//  with CustomExceptionHandler
{
  this: CORSHandler with RootContext =>
//  implicit val system: ActorSystem

  val routes: Route = concat(
    siteRoute,
    basicRoute,
    pathPrefix(getRootContext) {
      concat(
        testRoute
      )
    }
  )
}