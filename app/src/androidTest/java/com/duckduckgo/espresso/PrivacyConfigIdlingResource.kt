/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.espresso

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.espresso.IdlingResource
import logcat.logcat

class PrivacyConfigIdlingResource(
    private val context: Context,
    private val prefsName: String,
    private val key: String,
) : IdlingResource {

    @Volatile
    private var callback: IdlingResource.ResourceCallback? = null

    private var isIdle = false

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 500L
    private val timeoutMillis = 60_000L
    private val startTime = SystemClock.elapsedRealtime()

    override fun getName(): String = "PrivacyConfigIdlingResource for $key"

    override fun isIdleNow(): Boolean = isIdle

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
        poll()
    }

    private fun poll() {
        if (SystemClock.elapsedRealtime() - startTime > timeoutMillis) {
            isIdle = true
            callback?.onTransitionToIdle()
            throw AssertionError("Privacy config '$key' settings did not appear within timeout.")
        }

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val stateJson = prefs.getString(key, null)

        if (stateJson != null && stateJson.contains("\"settings\"")) {
            logcat(tag = "RadoiuC") {
                "Privacy config '$key' is ready with settings: $stateJson"
            }
            isIdle = true
            callback?.onTransitionToIdle()
        } else {
            logcat(tag = "RadoiuC") {
                "Privacy config '$key' is not ready yet. Current value: $stateJson"
            }
            handler.postDelayed({ poll() }, checkInterval)
        }
    }
}
