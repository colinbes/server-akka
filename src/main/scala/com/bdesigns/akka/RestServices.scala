package com.bdesigns.akka

import com.bdesigns.akka.rest.{BasicService, SiteService, TestService}

trait RestServices extends TestService
  with SiteService
  with BasicService {
  this: CORSHandler =>
}
