package orco

import zio._

package object config extends Config.Service[Config] {
    def load(): RIO[Config, AppConfig] = ZIO.accessM(_.config.load)
}
