package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive

class TwitterUsersActor extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case watchStockT@WatchStockT(symbol) =>
      // get or create the StockActor for the symbol and forward this message
      context.child(symbol).getOrElse {
        context.actorOf(Props(new TwitterUserActor(symbol)), symbol)
      } forward watchStockT
    case unwatchStockT@UnwatchStockT(Some(symbol)) =>
      // if there is a StockActor for the symbol forward this message
      context.child(symbol).foreach(_.forward(unwatchStockT))
    case unwatchStockT@UnwatchStockT(None) =>
      // if no symbol is specified, forward to everyone
      context.children.foreach(_.forward(unwatchStockT))
  }

}
