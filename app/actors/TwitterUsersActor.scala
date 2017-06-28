package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive

class TwitterUsersActor extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case watchTwitterUser@WatchTwitterUser(symbol) =>
      // get or create the Actor for the symbol and forward this message
      context.child(symbol).getOrElse {
        context.actorOf(Props(new TwitterUserActor(symbol)), symbol)
      } forward watchTwitterUser
    case unwatchTwitterUser@UnwatchTwitterUser(Some(symbol)) =>
      // if there is a Actor for the symbol forward this message
      context.child(symbol).foreach(_.forward(unwatchTwitterUser))
    case unwatchTwitterUser@UnwatchTwitterUser(None) =>
      // if no symbol is specified, forward to everyone
      context.children.foreach(_.forward(unwatchTwitterUser))
  }

}
