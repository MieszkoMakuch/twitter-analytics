package utils

import play.api.libs.ws.ning.NingWSClient
import scala.concurrent.ExecutionContext.Implicits.global

trait StockQuote {
  def newPrice(lastPrice: Double): Double
}

class FakeStockQuote extends StockQuote {
  private def random: Double = scala.util.Random.nextDouble

  def newPrice(lastPrice: Double): Double = {
    lastPrice * (0.95 + (0.1 * random))
  }
}

//class RealStockQuote extends StockQuote {
//  private def random: Double = scala.util.Random.nextDouble
//
//  def newPrice(lastPrice: Double): Double = {
//
//    // Instantiation of the client
//    // In a real-life application, you would instantiate one, share it everywhere,
//    // and call wsClient.close() when you're done
//    val wsClient = NingWSClient()
//    wsClient
//      .url("http://jsonplaceholder.typicode.com/comments/1")
//      .get()
//      .map { wsResponse =>
//        if (! (200 to 299).contains(wsResponse.status)) {
//          sys.error(s"Received unexpected status ${wsResponse.status} : ${wsResponse.body}")
//        }
//        println(s"OK, received ${wsResponse.body}")
//        println(s"The response header Content-Length was ${wsResponse.header("Content-Length")}")
//      }
//    lastPrice * (0.95 + (0.1 * random))
//  }
//}
