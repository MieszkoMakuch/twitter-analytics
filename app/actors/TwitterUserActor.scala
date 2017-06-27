package actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.{HashTag, Tweet, User}
import utils.{FakeStockQuote, StockQuote}

import scala.collection.immutable.{HashSet, Queue}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TwitterUserActor(userName: String) extends Actor with ActorLogging {

  def getTopHashtags(tweets: Seq[Tweet], n: Int = 10): Seq[(String, Int)] = {
    val hashtags: Seq[Seq[HashTag]] = tweets.map { tweet =>
      tweet.entities.map(_.hashtags).getOrElse(Seq.empty)
    }
    val hashtagTexts: Seq[String] = hashtags.flatten.map(_.text.toLowerCase)
    val hashtagFrequencies: Map[String, Int] = hashtagTexts.groupBy(identity).mapValues(_.size)
    hashtagFrequencies.toSeq.sortBy { case (entity, frequency) => -frequency }.take(n)
  }

  def getUserStats(sender: ActorRef, symbol: String) = {
    val client = TwitterRestClient()

//    val result = client.userTimelineForUser(screen_name = userName, count = 200).map { ratedData =>
//      val tweets = ratedData.data
//      val topHashtags: Seq[(String, Int)] = getTopHashtags(tweets)
////      sender ! StockHistoryT(userName, topHashtags)
//      watchers.foreach(_ ! TwitterUserStats(userName, topHashtags))
//      println("After sender ! StockHistoryT(userName, topHashtags)")
//    }

    val topHashtagsFuture = client.userTimelineForUser(screen_name = userName, count = 200).map { ratedData =>
      val tweets = ratedData.data
      val topHashtags: Seq[(String, Int)] = getTopHashtags(tweets)
      topHashtags
    }

    val userStats = for{
      userData <- client.user(screen_name = userName).map{_.data}
      topHashtags <- topHashtagsFuture
    } yield(userData, topHashtags)

    userStats.map { userStats =>
      println("image url userStats._1:" + userStats._1)
      watchers.foreach(_ ! TwitterUserStats(userName, userStats._1, userStats._1.profile_image_url.default, userStats._2))
    }
  }

  protected[this] var watchers: HashSet[ActorRef] = HashSet.empty[ActorRef]

  private val fetchLatestInterval = 10000.millis
  // Fetch the latest stock value every 75ms
  val stockTick = {
    // scheduler should use the system dispatcher
    context.system.scheduler.schedule(Duration.Zero, fetchLatestInterval, self, FetchLatestT)(context.system.dispatcher)
  }

  def receive = LoggingReceive {
    case FetchLatestT =>
      // notify watchers
      getUserStats(sender, userName)
    case WatchStockT(_) =>
      Console.print(s"Received case WatchStockT(_) =>\n")
      // add the watcher to the list
      watchers = watchers + sender
      // send the stock history to the user
//      sender ! StockHistoryT(symbol, stockHistory.toList)
      getUserStats(sender, userName)
    case UnwatchStockT(_) =>
      watchers = watchers - sender
      if (watchers.isEmpty) {
        stockTick.cancel()
        context.stop(self)
      }
  }
}


case object FetchLatestT

case class StockUpdateT(symbol: String, price: Number)

case class TwitterUserStats(username: String, userData: User, profileImageUrl: String, topHashtags: Seq[(String, Int)])

case class WatchStockT(symbol: String)

case class UnwatchStockT(symbol: Option[String])
