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

import org.slf4j.MDC

/** Provides a stable store of MDC data relating to a Request.
  *
  * As long as all data to be added to the MDC is added via `RequestMdc`
  * (`RequestMdc.add(request.id, Map("mykey", "myval"))`), then
  * `RequestMdc.initMdc(request.id)` can be called whenever a Request
  * is in scope to ensure the thread has the correct MDC.
  */
object RequestMdc {

  private[mdc] val mdcData =
    // RequestId -> MdcData
    // Note, WeakHashMap is mutable - the AtomicReference just serves to
    // ensure appropriate synchronisation (calls like getAndUpdate will not behave as expected)
    new java.util.concurrent.atomic.AtomicReference(
      // Note, converting to Scala with asScala breaks the weak reference keys
      new java.util.WeakHashMap[Long, Map[String, String]]()
    )

  /** Clears the MDC for the provided requestId */
  def clear(requestId: Long): Unit =
    mdcData.getAndUpdate { map =>
      // This is side-effecting, but should be fine to be repeated if getAndUpdate has a collision.
      // Ideally we'd make the side-effect after getAndUpdate, but it will return the updated map
      // since WeakHashMap is mutable.
      Option(map.get(requestId)).getOrElse(Map.empty)
        .foreach { case (k, _) => MDC.remove(k) }
      map.remove(requestId)
      map
    }

  /** Adds the provided data to any existing MDC for the provided requestId */
  def add(requestId: Long, data: Map[String, String]): Unit = {
    mdcData.updateAndGet { map =>
      map.put(
        requestId,
        Option(map.get(requestId)).fold(data)(_ ++ data)
      )
      map
    }
    initMdc(requestId)
  }

  /** Remove specific data from MDC for the provided requestId and data key */
  def remove(requestId: Long, key: String): Unit = {
    mdcData.getAndUpdate { map =>
      map.put(requestId, Option(map.get(requestId)).getOrElse(Map.empty).removed(key))
      map
    }
    MDC.remove(key)
  }

  /** Ensures the MDC is populated with only the data previously registered with `RequestMdc`.
    * It can be called periodically, to ensure MDC is correct. E.g. after an async boundary.
    */
  def initMdc(requestId: Long): Unit =
    // We're not replacing the whole ContextMap since there may other MDC added that we don't know about
    // (not added via `RequestMdc`)
    // However, that data is at risk of not being preserved properly and should be added via the MdcHolder.
    Option(mdcData.get().get(requestId)).getOrElse(Map.empty)
      .foreach { case (k, v) => MDC.put(k, v) }
}
