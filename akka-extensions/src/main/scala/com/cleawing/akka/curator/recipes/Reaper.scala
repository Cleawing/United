package com.cleawing.akka.curator.recipes

import akka.actor.{Terminated, ActorRef, Props, Actor}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.locks
import scala.collection.concurrent.TrieMap
import java.nio.file.Paths

private[curator] class Reaper(curator: CuratorFramework, reapingThresholdMs: Int) extends Actor {
  import scala.collection.mutable
  import com.cleawing.akka.curator.Curator
  private val reaper = new locks.Reaper(curator, reapingThresholdMs)
  private val pathOwners = TrieMap.empty[ActorRef, Set[String]]

  override def preStart(): Unit = {
    reaper.start()
  }

  def receive = {
    case Curator.Reaper.AddPath(path, recursiveFrom, mode) =>
      if (recursiveFrom.isDefined) {
        var currentPath = "/"
        val byPassFor = Paths.get("/", recursiveFrom.get).toString
        val pathIterator = Paths.get(path).iterator()
        var pathsToAdd = mutable.ListBuffer.empty[String]
        while (pathIterator.hasNext) {
          currentPath = Paths.get(currentPath, pathIterator.next().toString).toString
          if (currentPath != byPassFor) pathsToAdd += currentPath
        }
        addPaths(mode, pathsToAdd:_*)
      } else
        addPaths(mode, path)

    case Curator.Reaper.RemovePath(path) =>
      val paths = pathOwners.getOrElseUpdate(sender(), Set.empty[String])
      if (paths.contains(path))
        reaper.removePath(path)
        pathOwners(sender()) = paths - path

    case Terminated(ref) if pathOwners.contains(ref) =>
      pathOwners.remove(ref).getOrElse(Seq()).foreach(reaper.removePath)
  }

  override def postStop(): Unit = {
    reaper.close()
  }

  private def addPaths(mode: Reaper.Mode.Value, paths: String*): Unit = {
    for (path <- paths) {
      reaper.addPath(path, resolveMode(mode))
      val currentPaths = pathOwners.getOrElseUpdate(sender(), Set.empty[String])
      pathOwners(sender()) = currentPaths + path
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
