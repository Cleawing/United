package com.cleawing.consul.api

import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.JsonMethods.parse

object Unmarshallers {
  import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.stringUnmarshaller
  import Response._

  private type FEU[T] = FromEntityUnmarshaller[T]
  protected implicit val formats = Serialization.formats(NoTypeHints)

  implicit def seqStringUnmarshaller(implicit fm: Materializer) : FEU[Seq[String]] = {
    stringUnmarshaller(fm) mapWithInput{ (_, s) =>
      parse(s).extract[Seq[String]]
    }
  }

  implicit def checkStatesUnmarshaller(implicit fm: Materializer) : FEU[CheckDescriptors] = {
    stringUnmarshaller(fm) mapWithInput{ (_, s) =>
      Serialization.read[CheckDescriptors](s)
    }
  }

  implicit def serviceDescriptorsUnmarshaller(implicit fm: Materializer) : FEU[ServiceDescriptors] = {
    stringUnmarshaller(fm) mapWithInput{ (_, s) =>
      Serialization.read[ServiceDescriptors](s)
    }
  }

  implicit def membersUnmarshaller(implicit fm: Materializer) : FEU[Members] = {
    stringUnmarshaller(fm) mapWithInput{ (_, s) =>
      Serialization.read[Members](s)
    }
  }

  implicit def agentSelfUnmarshaller(implicit fm: Materializer) : FEU[Self] = {
    stringUnmarshaller(fm) mapWithInput{ (_, s) =>
      Serialization.read[Self](s)
    }
  }
}
