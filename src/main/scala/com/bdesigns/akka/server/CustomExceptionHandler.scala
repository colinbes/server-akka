package com.bdesigns.akka
package server

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.StatusCodes.Conflict
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.extractUri
import akka.http.scaladsl.server.Directives.respondWithHeader
import akka.http.scaladsl.server.ExceptionHandler

trait CustomExceptionHandler {
  val logger:LoggingAdapter

  implicit def myExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      extractUri { uri =>
        logger.warning("Illegal Argument", uri)
        complete {
          InternalServerError -> s"Illegal Argument ${e.getMessage()}"
        }
      }
    case ex =>
      extractUri { uri =>
        complete {
          logger.warning(s"got exception $ex")
          BadRequest -> s"Exception ${ex.getMessage()}"
        }
      }
  }

}
