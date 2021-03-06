/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zimmob.zimlx.smartspace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import com.android.launcher3.R
import org.zimmob.zimlx.dayOfYear
import org.zimmob.zimlx.hourOfDay
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

@Keep
class PersonalityProvider(controller: ZimSmartspaceController) :
        ZimSmartspaceController.DataProvider(controller) {
    private val updateInterval = 60 * 1000
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            onUpdate()
        }
    }

    var time = currentTime()
    var randomIndex = 0
    val isMorning get() = time.hourOfDay in 5 until 9
    val isEvening get() = time.hourOfDay in 19 until 24 || time.hourOfDay == 0
    val morningGreeting get() = morningStrings[randomIndex % morningStrings.size]
    val eveningGreeting get() = eveningStrings[randomIndex % eveningStrings.size]

    private val morningStrings = controller.context.resources.getStringArray(R.array.greetings_morning)
    private val eveningStrings = controller.context.resources.getStringArray(R.array.greetings_evening)

    private val handler = Handler(Looper.getMainLooper())
    private val onUpdateRunnable = ::onUpdate

    private fun currentTime() = Calendar.getInstance()

    override fun performSetup() {
        super.performSetup()
        context.registerReceiver(
                timeReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                })
        onUpdate()
    }

    private fun onUpdate() {
        time = currentTime()
        randomIndex = abs(Random(time.dayOfYear).nextInt())
        updateData(null, getEventCard())

        val now = System.currentTimeMillis()
        handler.removeCallbacks(onUpdateRunnable)
        handler.postDelayed(onUpdateRunnable, updateInterval - now % updateInterval)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(onUpdateRunnable)
    }

    private fun getEventCard(): ZimSmartspaceController.CardData? {
        val lines = mutableListOf<ZimSmartspaceController.Line>()
        when {
            isMorning -> lines.add(ZimSmartspaceController.Line(morningGreeting))
            isEvening -> lines.add(ZimSmartspaceController.Line(eveningGreeting))
            else -> return null
        }
        return ZimSmartspaceController.CardData(
                lines = lines,
                forceSingleLine = true)
    }
}