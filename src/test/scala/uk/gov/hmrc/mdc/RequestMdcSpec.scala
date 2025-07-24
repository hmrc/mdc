/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.mdc

import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RequestMdcSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with BeforeAndAfter {

  before {
    org.slf4j.MDC.clear()
  }

  private val random = new scala.util.Random()

  "RequestMdc.add" should {
    "store data to Mdc" in {
      val requestId = random.nextLong()

      val data = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data)

      Mdc.mdcData shouldBe data
    }

    "complement any unmanaged Mdc" in {
      val requestId = random.nextLong()

      val managed   = Map("a" -> "1", "b" -> "2")
      val unmanaged = Map("c" -> "3")
      Mdc.putMdc(unmanaged)
      RequestMdc.add(requestId, managed)

      Mdc.mdcData shouldBe (managed ++ unmanaged)
    }

    "init data to Mdc" in {
      val requestId = random.nextLong()

      val data = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data)

      val result = new java.util.concurrent.ArrayBlockingQueue[Map[String, Boolean]](1)

      val t = new Thread {
        override def run(): Unit = {
          val beforeInit = Mdc.mdcData == data
          RequestMdc.initMdc(requestId)
          val afterInit = Mdc.mdcData == data
          result.put(Map("beforeInit" -> beforeInit, "afterInit" -> afterInit))
        }
      }
      t.start()
      result.take() shouldBe Map("beforeInit" -> false, "afterInit" -> true)
    }

    "append to existing data" in {
      val requestId = random.nextLong()

      val data1 = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data1)

      val data2 = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data2)

      Mdc.mdcData shouldBe (data1 ++ data2)
    }

    "not lead to OOM" in {
      def randomString(length: Int): String = {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        (1 to length).map(_ => chars(random.nextInt(chars.length))).mkString
      }

      val numEntries = 20000
      for (requestId <- 1 to numEntries)
        RequestMdc.add(requestId.toLong, Map("k" -> randomString(1000)))
      // we're still here, so no OOM
      RequestMdc.mdcData.get().size should be < numEntries
    }
  }

  "RequestMdc.remove" should {
    "remove data from Mdc" in {
      val requestId = random.nextLong()

      RequestMdc.add(requestId, Map("a" -> "1", "b" -> "2"))

      RequestMdc.remove(requestId, "a")

      Mdc.mdcData shouldBe Map("b" -> "2")
    }

    "not error if empty" in {
      val requestId = random.nextLong()

      RequestMdc.remove(requestId, "a")

      Mdc.mdcData shouldBe Map.empty
    }

    "not error if key not found" in {
      val requestId = random.nextLong()

      val data = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data)

      RequestMdc.remove(requestId, "c")

      Mdc.mdcData shouldBe data
    }
  }

  "RequestMdc.clear" should {
    "clear data" in {
      val requestId = random.nextLong()

      val data = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data)

      RequestMdc.clear(requestId)

      Mdc.mdcData shouldBe Map.empty
    }

    "not clear unmanaged MDC data" in {
      val requestId = random.nextLong()

      val data = Map("a" -> "1", "b" -> "2")
      RequestMdc.add(requestId, data)
      Mdc.putMdc(Map("c" -> "3"))

      RequestMdc.clear(requestId)

      Mdc.mdcData shouldBe Map("c" -> "3")
    }
  }
}
