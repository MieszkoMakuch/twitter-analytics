import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  import actors._

  override def configure(): Unit = {
    // Stock actor
    bindActor[StocksActor]("stocksActor")
    // Twitter user actor
    bindActor[TwitterUsersActor]("twitterUsersActor")
    bindActor[UserParentActor]("userParentActor")
    bindActorFactory[UserActor, UserActor.Factory]
  }
}
