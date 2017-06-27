package actors

import javax.inject._

import akka.actor._
import akka.event.LoggingReceive
import com.danielasfregola.twitter4s.entities.User
import com.google.inject.assistedinject.Assisted
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.json._
import utils.JsonWrites

class UserActor @Inject()(@Assisted out: ActorRef,
                          @Named("stocksActor") stocksActor: ActorRef,
                          @Named("twitterUsersActor") twitterUsersActor: ActorRef,
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
    case StockUpdateT(symbol, price) =>
      val stockUpdateMessage = Json.obj("type" -> "twitterUserStatsUpdate", "symbol" -> symbol, "price" -> price.doubleValue())
      out ! stockUpdateMessage

    case TwitterUserStats(username, userData, profileImageUrl, topHashtags) =>
      print(userData.toString)
      val topHashtagsJson = topHashtags map {case (hashtag: String, frequency: Int) => Seq(Json.toJson(hashtag), Json.toJson(frequency))}
      val userDataJson = JsonWrites.userDataToJson(userData)
      val stockUpdateMessage = Json.obj("type" -> "twitterUserStats", "userData" -> userDataJson, "topHashtags" -> topHashtagsJson)
      out ! stockUpdateMessage


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
          Console.print(s"Received twitterUser, username=$userName\n")
          twitterUsersActor ! WatchStockT(userName)
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
