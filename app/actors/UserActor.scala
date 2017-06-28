package actors

import javax.inject._

import akka.actor._
import akka.event.LoggingReceive
import com.danielasfregola.twitter4s.entities.{LocationTrends, Trend, User}
import com.google.inject.assistedinject.Assisted
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.json._
import utils.JsonWrites

class UserActor @Inject()(@Assisted out: ActorRef,
                          @Named("stocksActor") stocksActor: ActorRef,
                          @Named("twitterUsersActor") twitterUsersActor: ActorRef,
                          @Named("locationsActor") locationsActor: ActorRef,
                          configuration: Configuration) extends Actor with ActorLogging {


  override def preStart(): Unit = {
    super.preStart()

    configureDefaultStocks()
  }

  def configureDefaultStocks(): Unit = {
    import collection.JavaConversions._
    val defaultStocks = configuration.getStringList("default.stocks").get
    log.info(s"Creating user actor with default stocks $defaultStocks")

    for (stockSymbol <- defaultStocks) {
      stocksActor ! WatchStock(stockSymbol)
    }
  }

  override def receive: Receive = LoggingReceive {
    // Stock
    case StockUpdate(symbol, price) =>
      val stockUpdateMessage = Json.obj("type" -> "stockupdate", "symbol" -> symbol, "price" -> price.doubleValue())
      out ! stockUpdateMessage

    case StockHistory(symbol, history) =>
      val numberSeq = history.map(h => Json.toJson[Double](h))
      val stockUpdateMessage = Json.obj("type" -> "stockhistory", "symbol" -> symbol, "history" -> numberSeq)
      out ! stockUpdateMessage

    // Twitter user
    case TwitterUserStats(username, userData, profileImageUrl, topHashtags) =>
      val topHashtagsJson = topHashtags map {case (hashtag: String, frequency: Int) => Seq(Json.toJson(hashtag), Json.toJson(frequency))}
      val userDataJson = JsonWrites.userDataToJson(userData)
      val twitterUserUpdate = Json.obj("type" -> "twitterUserStats", "userData" -> userDataJson, "topHashtags" -> topHashtagsJson)
      out ! twitterUserUpdate

    // Location
    case LocationStats(name, trends) =>
      println("case LocationStats(username, userData, profileImageUrl, locationTrends) =>")

      val locationTrendsJson = trends map {trend: Trend => Json.obj("name" -> trend.name, "url" -> trend.url)}
      val locationStatsUpdate = Json.obj(
        "type" -> "locationStats",
        "name" -> name,
        "locationTrends" -> locationTrendsJson)
      out ! locationStatsUpdate


    // From user
    case json: JsValue =>
      // When the user types in a stock in the upper right corner, this is triggered

      val msgType = (json \ "type").as[String]
      msgType match {
        case "stock" =>
          val symbol = (json \ "symbol").as[String]
          stocksActor ! WatchStock(symbol)
        case "twitterUser" =>
          val userName = (json \ "userName").as[String]
          twitterUsersActor ! WatchTwitterUser(userName)
        case "location" =>
          println("case \"location\" =>" + json.toString())
          val locationLat = (json \ "locationLat").as[String].toDouble
          val locationLng = (json \ "locationLng").as[String].toDouble

          locationsActor ! WatchLocation(LocationCoordinates(locationLat, locationLng))
      }
  }
}

class UserParentActor @Inject()(childFactory: UserActor.Factory) extends Actor with InjectedActorSupport with ActorLogging {
  import UserParentActor._

  override def receive: Receive = LoggingReceive {
    case Create(id, out) =>
      val child: ActorRef = injectedChild(childFactory(out), s"userActor-$id")
      sender() ! child
  }
}

object UserParentActor {
  case class Create(id: String, out: ActorRef)
}

object UserActor {
  trait Factory {
    // Corresponds to the @Assisted parameters defined in the constructor
    def apply(out: ActorRef): Actor
  }
}
