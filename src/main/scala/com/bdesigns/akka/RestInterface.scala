package com.bdesigns.akka

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors

trait RestInterface extends RestServices
//  with CustomExceptionHandler
{
  this: RootContext =>
//  implicit val system: ActorSystem

  val routes: Route = cors() {
    concat(
      siteRoute,
      pathPrefix(getRootContext) {
        concat(
          basicRoute,
          testRoute
        )
      }
    )
  }
}