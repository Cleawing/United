package com.cleawing.finagle.http

import com.cleawing.finagle.http.RamlClient.Params
import com.twitter.finagle.httpx.{Method, Request}
import org.raml.model.parameter.AbstractParam
import org.raml.model.{ParamType, Action, Resource}
import org.raml.parser.visitor.RamlDocumentBuilder
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization


import scala.util.Try

import scala.collection.JavaConversions._

class RamlHelper(resourceLocation: String) {
  protected implicit val formats = Serialization.formats(NoTypeHints)
  val raml = new RamlDocumentBuilder().build(resourceLocation)

  def buildRequestWithAction(uri: String, method: String,
                   uriParams: Seq[Params] = Seq[Params](),
                   queryParams: Seq[Params] = Seq[Params](),
                   payload: Option[AnyRef] = None) : Try[(Request, Action)] = {

    Try {
      raml.getResource(uri) match {
        case null =>  throw new IllegalArgumentException(s"Unknown uri '$uri'")
        case resource => resource.getAction(method) match {
          case null => throw new IllegalArgumentException(s"Unknown method '$method' for uri '$uri'")
          case action =>
            val uri = buildUri(resource, action, uriParams, queryParams)
            val request = Request(Method(action.getType.toString), s"${raml.getBasePath}$uri")
            if (action.getBody != null && action.getBody.contains("application/json")) {
              if (payload.isEmpty)
                throw new IllegalArgumentException("Payload should be defined")
              request.setContentTypeJson()
              request.setContentString(Serialization.write(payload.get))
            }
            (request, action)
        }
      }
    }
  }
  
  def buildUri(resource: Resource, action: Action, uriParams: Seq[Params], queryParams: Seq[Params]) : String = {
    var uri = resource.getUri
    try {
      validateParams(resource.getResolvedUriParameters, uriParams.toMap, alwaysRequired = true).foreach {
        case (paramName, paramValue) =>
          // FIXME. Param value uri-encoding
          uri = uri.replace(s"{$paramName}", paramValue)
      }
    } catch {
      case ex : IllegalArgumentException => throw new IllegalArgumentException(s"Uri param validation failed. ${ex.getMessage}")
    }

    try {
      uri = Request.queryString(uri, validateParams(action.getQueryParameters, queryParams.toMap))
    } catch {
      case ex : IllegalArgumentException => throw new IllegalArgumentException(s"Query param validation failed. ${ex.getMessage}")
    }
    uri
  }

  def validateParams(ramlParams: java.util.Map[String, _ <: AbstractParam],
                     requestParams: Map[String, String],
                     alwaysRequired : Boolean = false) : Map[String, String] = {
    ramlParams.filter {
      case (paramName, param) =>
        if ((alwaysRequired || param.isRequired) && !requestParams.contains(paramName))
          throw new IllegalArgumentException(s"'$paramName' is required")
        else
          requestParams.contains(paramName)
    }.map {
      case (paramName, param) =>
        val paramValue = requestParams(paramName)
        val validationMessage = param.getType.message(param, paramValue)
        if (validationMessage != ParamType.OK)
          throw new IllegalArgumentException(s"'$paramName': $validationMessage")
        else
          paramName -> paramValue
    }.toMap
  }
}

object RamlHelper {
  def apply(resourceLocation: String) : RamlHelper = new RamlHelper(resourceLocation)
}
