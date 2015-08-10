package com.cleawing.akka

import java.util.concurrent.atomic.AtomicInteger

import _root_.kafka.serializer.{StringEncoder, StringDecoder}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.softwaremill.react.kafka.{ReactiveKafka, ProducerProperties, ConsumerProperties}
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.{KafkaAvroEncoder, KafkaAvroDecoder}

import scala.concurrent.Future

object Main extends App {
  implicit val actorSystem = ActorSystem("ReactiveKafka")
  implicit val materializer = ActorMaterializer()
  val kafka = new ReactiveKafka()
  val schemaClient = new CachedSchemaRegistryClient("http://192.168.99.100:8081", 1000)

  args.toList match {
    case "producer" :: count :: batchSize :: bufferMaxMs :: requiredAcks :: Nil =>
      val subscriber = kafka.publish[String](ProducerProperties(
        brokerList = "192.168.99.100:9092",
        topic = "perf",
        clientId = "perf",
        encoder = new StringEncoder(),
        partitionizer = (msg: String) => Some(msg.getBytes("UTF-8"))
      ).setProperty("queue.buffering.max.messages",(batchSize.toInt + 10000).toString)
        .asynchronous(batchSize.toInt, bufferMaxMs.toInt)
        .requestRequiredAcks(requiredAcks.toInt)
      )
      Source(1 to count.toInt).map(_.toString).to(Sink(subscriber)).run()
    case "consumer" :: partitions :: Nil =>
      val counter = new AtomicInteger(0)
      (1 to partitions.toInt).map{ partition =>
        kafka.consume[String](ConsumerProperties(
            brokerList = "192.168.99.100:9092",
            zooKeeperHost = "192.168.99.100:2181/kafka",
            topic = "perf",
            groupId = "perf",
            decoder = new StringDecoder()
          )
          .setProperties(("auto.commit.interval.ms", "1000"), ("auto.offset.reset", "smallest"))
          .kafkaOffsetsStorage(false)
        )
      }.foreach { publisher =>
        Source(publisher).runForeach{ msg =>
          val increment = counter.incrementAndGet()
          println(s"$msg -> $increment")
        }
      }
    case "empty" :: count :: Nil =>
      val emptyActor = actorSystem.actorOf(EmptyActor.props())
      import actorSystem.dispatcher
      Future {
        for(i <- 1 to count.toInt) emptyActor ! i
      }
    case _ =>
      actorSystem.terminate()
  }

  case class Perf(identity: String)
}
