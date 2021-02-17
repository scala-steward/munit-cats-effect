/*
 * Copyright 2021 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package munit

import cats.effect.{IO, SyncIO, Resource}
import cats.syntax.flatMap._

import scala.concurrent.Promise

trait CatsEffectFunFixtures extends FunFixtures { self: CatsEffectSuite =>

  object ResourceFixture {

    def apply[T](
        resource: Resource[IO, T]
    ): SyncIO[FunFixture[T]] =
      apply(
        resource,
        (_, _) => IO.unit,
        _ => IO.unit
      )

    def apply[T](
        resource: Resource[IO, T],
        setup: (TestOptions, T) => IO[Unit],
        teardown: T => IO[Unit]
    ): SyncIO[FunFixture[T]] = SyncIO {
      val promise = Promise[IO[Unit]]()

      FunFixture.async(
        setup = { testOptions =>
          val resourceEffect = resource.allocated
          val setupEffect =
            resourceEffect
              .map { case (t, release) =>
                promise.success(release)
                t
              }
              .flatTap(t => setup(testOptions, t))

          setupEffect.unsafeToFuture()
        },
        teardown = { (argument: T) =>
          teardown(argument)
            .flatMap(_ => IO.fromFuture(IO(promise.future)).flatten)
            .unsafeToFuture()
        }
      )
    }

  }

  implicit class SyncIOFunFixtureOps[T](private val fixture: SyncIO[FunFixture[T]]) {
    def test(name: String)(
        body: T => Any
    )(implicit loc: Location): Unit = {
      fixture.unsafeRunSync().test(TestOptions(name))(body)
    }

    def test(options: TestOptions)(
        body: T => Any
    )(implicit loc: Location): Unit = {
      fixture.unsafeRunSync().test(options)(body)
    }
  }

}
