package com.cleawing.finagle.http

import com.twitter.finagle.{Service, Httpx}
import com.twitter.finagle.httpx.{Request, Response}

import scala.concurrent.Future

class Client(val host: String, val port: Int, label: String) {
  import com.cleawing.finagle.Utils.Implicits._

  private lazy val client: Service[Request, Response] = Httpx.newClient(s"$host:$port", label).toService

  def apply(request: Request): Future[Response] = client(request)
  def close(): Future[Unit] = client.close()
}

object Client {
  def apply(host: String, port: Int, label: String) : Client = new Client(host, port, label)

  object StatusCodes {
    val info = 100 to 199
    val success = 200 to 299
    val redirect = 300 to 399
    val clientError = 400 to 499
    val serverError = 500 to 599
  }
}
