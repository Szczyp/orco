package orco.config

import pureconfig.ConfigSource
import zio._

case class AppConfig(endpoint: String, port: Int, token: Option[String], connections: Int)

case class ConfigError(msg: String) extends RuntimeException(msg)

trait Config extends Serializable {
  val config: Config.Service[Any]
}

object Config {
  trait Service[R] {
    def load(): RIO[R, AppConfig]
  }

  trait Live extends Config {
    val config: Service[Any] = new Service[Any] {
      import pureconfig.generic.auto._

      val ref: UIO[Ref[Option[AppConfig]]] = Ref.make(None)

      val load: Task[AppConfig] = ref.flatMap(_.get).flatMap {
        case None =>
          for {
            c <- IO
                  .fromEither(ConfigSource.default.load[AppConfig])
                  .mapError(e => ConfigError(e.toList.mkString(", ")))
            _ <- ref.flatMap(_.set(Some(c)))
          } yield c
        case Some(c) => ZIO.succeed(c)
      }
    }
  }
  object Live extends Live

  trait Test extends Config {
    val config: Service[Any] = new Service[Any] {
      val load: Task[AppConfig] = Task.effectTotal(
        AppConfig("127.0.0.1", 8080, Some("token"), 10)
      )
    }
  }
  object Test extends Test
}
