package utils
import com.danielasfregola.twitter4s.entities.User
import play.api.libs.json._
/**
  * Created by mieszkomakuch on 27.06.2017.
  */
object JsonWrites {
  def userDataToJson(userData: User) = {
    Json.obj(
      "screen_name" -> userData.screen_name,
      "name" -> userData.name,
      "profile_image_url" -> userData.profile_image_url.default,
      "followers_count" -> userData.followers_count,
      "created_at" -> userData.created_at,
      "statuses_count" -> userData.statuses_count
    )
  }
}
