package com.bdesigns.akka.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{HttpCookie, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.bdesigns.akka.json.Json4sFormat
import org.slf4j.Logger

trait SiteService extends Json4sFormat {

  val logger:Logger
  val CookieName: String

  val siteRoute: Route = concat (
    pathSingleSlash {
      redirect("/index.html", StatusCodes.Found)
    },
    pathPrefix("") {
      get {
        val uuid = java.util.UUID.randomUUID.toString
        logger.warn(s"new uuid $uuid")
        setCookie(HttpCookie(CookieName, uuid)) {
          respondWithHeaders(RawHeader("x-my-header", "my-akka-test")) {
            getFromResourceDirectory("")
          }
        }
      }
    }
  )
}
