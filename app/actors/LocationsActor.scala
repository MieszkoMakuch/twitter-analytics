package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive

class LocationsActor extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case watchLocation@WatchLocation(locationCoordinates) =>
      // get or create the Actor for the symbol and forward this message
      context.child(locationCoordinates.toActorName).getOrElse {
        context.actorOf(Props(
          new LocationActor(locationCoordinates.lat, locationCoordinates.lng)),
          name = locationCoordinates.toActorName)
      } forward watchLocation
    case unwatchLocation@UnwatchLocation(Some(locationCoordinates)) =>
      // if there is a Actor for the symbol forward this message
      context.child(locationCoordinates).foreach(_.forward(unwatchLocation))
    case unwatchLocation@UnwatchLocation(None) =>
      // if no symbol is specified, forward to everyone
      context.children.foreach(_.forward(unwatchLocation))
  }

}
