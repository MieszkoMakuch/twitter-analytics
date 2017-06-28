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

  private def getTopHashtags(tweets: Seq[Tweet], n: Int = 5): Seq[(String, Int)] = {
    val hashtags: Seq[Seq[HashTag]] = tweets.map { tweet =>
      tweet.entities.map(_.hashtags).getOrElse(Seq.empty)
    }
    val hashtagTexts: Seq[String] = hashtags.flatten.map(_.text.toLowerCase)
    val hashtagFrequencies: Map[String, Int] = hashtagTexts.groupBy(identity).mapValues(_.size)
    hashtagFrequencies.toSeq.sortBy { case (entity, frequency) => -frequency }.take(n)
  }

  private def getUserStats(sender: ActorRef, symbol: String) = {
    val client = TwitterRestClient()

    val topHashtagsFuture = client.userTimelineForUser(screen_name = userName, count = 200).map { ratedData =>
      println("Twitter user rate limit: " + ratedData.rate_limit)
      val tweets = ratedData.data
      val topHashtags: Seq[(String, Int)] = getTopHashtags(tweets)
      topHashtags
    }

    val userStats = for{
      userData <- client.user(screen_name = userName).map{_.data}
      topHashtags <- topHashtagsFuture
    } yield(userData, topHashtags)

    userStats.map { userStats =>
      watchers.foreach(_ ! TwitterUserStats(userName, userStats._1, userStats._1.profile_image_url.default, userStats._2))
    }
  }

  protected[this] var watchers: HashSet[ActorRef] = HashSet.empty[ActorRef]

  // send updated stats
  private val updateInterval = 10000.millis
  private val twitterUserTick = {
    // scheduler should use the system dispatcher
    context.system.scheduler.schedule(Duration.Zero, updateInterval, self, TwitterUserUpdate)(context.system.dispatcher)
  }

  def receive = LoggingReceive {
    case TwitterUserUpdate =>
      // send watchers updated stats
      getUserStats(sender, userName)
    case WatchTwitterUser(_) =>
      // add the watcher to the list
      watchers = watchers + sender
      // send the stock history to the user
      getUserStats(sender, userName)
    case UnwatchTwitterUser(_) =>
      watchers = watchers - sender
      if (watchers.isEmpty) {
        twitterUserTick.cancel()
        context.stop(self)
      }
  }
}


case object TwitterUserUpdate

case class TwitterUserStats(username: String, userData: User, profileImageUrl: String, topHashtags: Seq[(String, Int)])

case class WatchTwitterUser(symbol: String)

case class UnwatchTwitterUser(symbol: Option[String])
