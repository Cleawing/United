package com.cleawing.finagle

import com.twitter.util.{Future => TFuture}
import scala.concurrent.{Promise, Future}
import scala.language.implicitConversions
import scala.util.{Try, Success, Failure}

object Utils {
  object Implicits {
    implicit def twitterFuture2ScalaFuture[T](future: TFuture[T]) : Future[T] = {
      val promise = Promise[T]()
      future
        .onSuccess(promise.success(_))
        .onFailure(promise.failure(_))
      promise.future
    }
  }
}