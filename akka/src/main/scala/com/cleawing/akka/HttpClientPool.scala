package com.cleawing.akka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, Marshal}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.{Failure, Try}

trait HttpClientPool[Request] {
  this : Actor =>
    import context.dispatcher

    def host : String
    def port : Int

    private val requestsCounter = new AtomicInteger(0)
    private val senders = TrieMap.empty[Int, ActorRef]

    protected implicit val materializer = ActorMaterializer()
    private val poolClientFlow = Http()(context.system).cachedHostConnectionPool[Int](host, port)

    protected def makeRequest(request: Request)(implicit m: Marshaller[Request, HttpRequest]) : Future[(Try[HttpResponse], Option[ActorRef])] = {
      val inFlightRequestCounter = requestsCounter.incrementAndGet()
      senders.put(inFlightRequestCounter, sender())
      (for {
        httpRequest <- Marshal(request).to[HttpRequest]
        response <- Source.single(httpRequest -> inFlightRequestCounter)
          .via(poolClientFlow)
          .runWith(Sink.head)
        target <- Future(senders.remove(response._2).get)
      } yield {
          (response._1, Some(target))
      }).recover {
        case t: Throwable =>
          senders.remove(inFlightRequestCounter)
          (Failure(t), None)
      }
    }
}
