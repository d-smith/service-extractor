package dumpreader

import akka.actor.{Terminated, Actor, ActorRef}
import scala.collection.mutable.ArrayBuffer

//Shamelessly ripped off from http://letitcrash.com/post/30165507578/shutdown-patterns-in-akka-2
object Reaper {
  // Used by others to register an Actor for watching
  case class WatchMe(ref: ActorRef)
}

abstract class Reaper extends Actor {
  import Reaper._

  // Keep track of what we're watching
  val watched = ArrayBuffer.empty[ActorRef]

  // Derivations need to implement this method.  It's the
  // hook that's called when everything's dead
  def allSoulsReaped(): Unit

  // Watch and check for termination
  final def receive = {
    case WatchMe(ref) =>
      println(s"watching $ref")
      context.watch(ref)
      watched += ref
    case Terminated(ref) =>
      println(s"reaping $ref")
      watched -= ref
      if (watched.isEmpty) allSoulsReaped()
  }
}

class AllPersistedReaper extends Reaper {
  def allSoulsReaped() : Unit = context.system.shutdown()
}