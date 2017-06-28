package actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities._
import utils.{FakeStockQuote, StockQuote}

import scala.collection.immutable.{HashSet, Queue}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LocationActor(locationLat: Double, locationLng: Double) extends Actor with ActorLogging {

//  private def getTopHashtags(tweets: Seq[Tweet], n: Int = 5): Seq[(String, Int)] = {
//    val hashtags: Seq[Seq[HashTag]] = tweets.map { tweet =>
//      tweet.entities.map(_.hashtags).getOrElse(Seq.empty)
//    }
//    val hashtagTexts: Seq[String] = hashtags.flatten.map(_.text.toLowerCase)
//    val hashtagFrequencies: Map[String, Int] = hashtagTexts.groupBy(identity).mapValues(_.size)
//    hashtagFrequencies.toSeq.sortBy { case (entity, frequency) => -frequency }.take(n)
//  }

//  private def getUserStats(sender: ActorRef, symbol: String) = {
//    val client = TwitterRestClient()
//
//    val topHashtagsFuture = client.userTimelineForUser(screen_name = userName, count = 200).map { ratedData =>
//      val tweets = ratedData.data
//      val topHashtags: Seq[(String, Int)] = getTopHashtags(tweets)
//      topHashtags
//    }
//
//    val userStats = for{
//      userData <- client.user(screen_name = userName).map{_.data}
//      topHashtags <- topHashtagsFuture
//    } yield(userData, topHashtags)
//
//    userStats.map { userStats =>
//      watchers.foreach(_ ! LocationStats(userName, userStats._1, userStats._1.profile_image_url.default, userStats._2))
//    }
//  }

  private def getLocationStats(sender: ActorRef) = {
    val client = TwitterRestClient()

    lazy val nearMeTrendsFuture = for {
      locationNearestMe <- client.closestLocationTrends(locationLat, locationLng).map { closestLocationTrends =>
        println("closestLocationTrends.rate_limit: " + closestLocationTrends.rate_limit)
        closestLocationTrends.data
      } // lista bliskich lokacji
      woeId = locationNearestMe.headOption.map(_.woeid) //wybierz najbliższa, weź woeid
      if woeId.isDefined
      locationNearestMeResult <- client.trends(woeId.get).map(_.data) //weź trendy dla tej lokacji
    } yield locationNearestMeResult

    val locationStats = for{
      nearMeTrends <- nearMeTrendsFuture
    } yield nearMeTrends.head

    locationStats.map { locationStats =>
      watchers.foreach(_ ! LocationStats(locationStats.locations.head.name, locationStats.trends))
    }

  }

  protected[this] var watchers: HashSet[ActorRef] = HashSet.empty[ActorRef]

  // send updated stats
  private val updateInterval = 1000000.millis
  private val tick = {
    // scheduler should use the system dispatcher
    context.system.scheduler.schedule(Duration.Zero, updateInterval, self, LocationUpdate)(context.system.dispatcher)
  }

  def receive = LoggingReceive {
    case LocationUpdate =>
      // send watchers updated stats

      getLocationStats(sender)
    case WatchLocation(_) =>
      println("case WatchLocation(_) =>")
      // add the watcher to the list
      watchers = watchers + sender
      // send the stock history to the user
      getLocationStats(sender)
    case UnwatchLocation(_) =>
      watchers = watchers - sender
      if (watchers.isEmpty) {
        tick.cancel()
        context.stop(self)
      }
  }
}


case object LocationUpdate

case class LocationStats(locationName: String, trends: Seq[Trend])

case class WatchLocation(locationCoordinates: LocationCoordinates)

case class UnwatchLocation(symbol: Option[String])

case class LocationCoordinates(lat: Double, lng: Double){
  def toActorName = lat.toString + "-" + lng.toString
}
