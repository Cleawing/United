package com.cleawing.akka.curator.recipes

import akka.actor.{Terminated, ActorRef, Props, Actor}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.locks
import java.nio.file.Paths

private[curator] class Reaper(curator: CuratorFramework, reapingThresholdMs: Int) extends Actor {
  import scala.collection.concurrent.TrieMap
  import scala.collection.mutable
  import com.cleawing.akka.curator.Curator
  private val reaper = new locks.Reaper(curator, reapingThresholdMs)
  private val pathOwners = TrieMap.empty[ActorRef, mutable.Set[String]]

  override def preStart(): Unit = {
    reaper.start()
  }

  def receive = {
    case Curator.Reaper.AddPath(path, recursiveFrom, mode) =>
      if (recursiveFrom.isDefined) {
        var currentPath = "/"
        val byPassFor = Paths.get("/", recursiveFrom.get).toString
        val pathIterator = Paths.get(path).iterator()
        var pathsToReap = mutable.ListBuffer.empty[String]
        while (pathIterator.hasNext) {
          currentPath = Paths.get(currentPath, pathIterator.next().toString).toString
          if (currentPath != byPassFor) pathsToReap += currentPath
        }
        addPaths(mode, pathsToReap:_*)
      } else
        addPaths(mode, path)

    case Curator.Reaper.RemovePath(path) if pathOwners.contains(sender()) =>
      val paths = pathOwners(sender())
      if (paths.contains(path))
        reaper.removePath(path)
        pathOwners(sender()) -= path
      if (pathOwners(sender()).isEmpty) {
        context.unwatch(sender())
        pathOwners.remove(sender())
      }

    case Terminated(ref) if pathOwners.contains(ref) =>
      pathOwners.remove(ref).getOrElse(Seq()).foreach(reaper.removePath)
  }

  override def postStop(): Unit = {
    reaper.close()
  }

  private def addPaths(mode: Reaper.Mode.Value, pathsToReap: String*): Unit = {
    val paths = pathOwners.getOrElseUpdate(sender(), mutable.Set.empty[String])
    for (path <- pathsToReap) {
      reaper.addPath(path, resolveMode(mode))
      paths += path
    }
    context.watch(sender())
  }

  private def resolveMode(mode: Reaper.Mode.Value): locks.Reaper.Mode = mode match {
    case Reaper.Mode.REAP_INDEFINITELY => locks.Reaper.Mode.REAP_INDEFINITELY
    case Reaper.Mode.REAP_UNTIL_DELETE => locks.Reaper.Mode.REAP_UNTIL_DELETE
    case Reaper.Mode.REAP_UNTIL_GONE => locks.Reaper.Mode.REAP_UNTIL_GONE
  }
}

object Reaper {
  object Mode extends Enumeration {
    val REAP_INDEFINITELY, REAP_UNTIL_DELETE, REAP_UNTIL_GONE = Value
  }

  private[curator] def props(curator: CuratorFramework, reapingThresholdMs: Int): Props =
    Props(classOf[Reaper], curator, reapingThresholdMs)
}
