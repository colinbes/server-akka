package com.bdesigns.akka

import com.bdesigns.akka.rest.{BasicService, SiteService, TestService}

trait RestServices extends SiteService
  with BasicService
  with TestService
{
}
