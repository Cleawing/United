package com.cleawing.finagle.http

import com.twitter.finagle.httpx.Response

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import org.json4s.Formats
import org.json4s.jackson.JsonMethods.parse
import scala.collection.JavaConversions._

class RamlClient(host: String, port: Int, label: String)(implicit raml: RamlHelper, ec: ExecutionContext, formats: Formats) extends Client(host, port, label) {
  import RamlClient.Params
  import RamlClient.ResponseError
  import RamlClient.ResponseValidationError
  import scala.reflect.runtime.universe.typeOf

  def get[T: Manifest](uri: String, uriParams: Seq[Params] = Seq[Params](), queryParams: Seq[Params] = Seq[Params]())(implicit endpointUri: String) : Future[T] = {
    doRequest[T](s"$endpointUri/$uri", "get", uriParams, queryParams)
  }

  def post[T: Manifest](uri: String, uriParams: Seq[Params] = Seq[Params](), queryParams: Seq[Params] = Seq[Params](), payload: Option[AnyRef] = None)(implicit endpointUri: String) : Future[T] = {
    doRequest[T](s"$endpointUri/$uri", "post", uriParams, queryParams, payload)
  }

  def put[T: Manifest](uri: String, uriParams: Seq[Params] = Seq[Params](), queryParams: Seq[Params] = Seq[Params](), payload: Option[AnyRef] = None)(implicit endpointUri: String) : Future[T] = {
    doRequest[T](s"$endpointUri/$uri", "put", uriParams, queryParams, payload)
  }

  protected def doRequest[T: Manifest](uri: String, method: String, uriParams: Seq[Params] = Seq[Params](),
                queryParams: Seq[Params] = Seq[Params](), payload: Option[AnyRef] = None) : Future[T] = {
    val tpe = typeOf[T]
    raml.buildRequestWithAction(uri, method, uriParams, queryParams, payload).map {
      case (request, action) => apply(request).map { response =>
        val responses = action.getResponses
        if (!responses.contains(response.getStatusCode().toString))
          throw new ResponseValidationError(
            s"Response status code ${response.getStatusCode()} does not conform to expected: [${responses.keys.mkString(", ")}]",
            response
          )
        if (tpe =:= typeOf[Boolean]) {
          (response.getStatusCode() match {
            case 200 => true
            case code if Client.StatusCodes.clientError.contains(code) => false
            case _ => throw new ResponseError(response.getContentString(), response)
          }).asInstanceOf[T]
        }
        else if (tpe =:= typeOf[Unit]) {
          if (response.getStatusCode() != 200)
            throw new ResponseError(response.getContentString(), response)
          else ().asInstanceOf[T]
        } else {
          if ((Client.StatusCodes.clientError ++ Client.StatusCodes.serverError).contains(response.getStatusCode())) {
            throw new ResponseError(response.getContentString(), response)
          }
          val expectedResponse = responses(response.getStatusCode().toString)
          response.contentType match {
            case None =>
              if (expectedResponse.getBody.size() > 0)
                throw new ResponseValidationError(
                  s"Empty response content type does not conform to expected: [${expectedResponse.getBody.keys.mkString(", ")}}]",
                  response
                )
              else
                response.getContentString().asInstanceOf[T]
            case Some(contentType) =>
              if (expectedResponse.getBody != null && !expectedResponse.getBody.contains(contentType))
                throw new ResponseValidationError(
                  s"Response content type [$contentType] does not conform to expected: [${expectedResponse.getBody.keys.mkString(", ")}}]",
                  response
                )
              else contentType match {
                case "application/json" => parse(response.getContentString()).extract[T]
                case _ => response.getContentString().asInstanceOf[T]
              }
          }
        }
      }
    } match {
      case Success(s) => s
      case Failure(ex) => Future.failed(ex)
    }
  }
}

object RamlClient {
  import scala.language.implicitConversions
  def apply(host: String, port: Int, label: String)(implicit raml: RamlHelper, ec: ExecutionContext, formats: Formats): RamlClient = new RamlClient(host, port, label)
  type Params = (String, String)
  implicit def paramOptToSeq(opt: Option[Params]) : Seq[Params] = Seq.empty[Params] ++ opt.toList

  class ResponseError(message: String, response: Response) extends IllegalStateException(message)
  class ResponseValidationError(message: String, response: Response) extends ResponseError(message, response)
}
