package com.example.karmaloop

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class TrackingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var totalPoints = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "karma_channel")
            .setContentTitle("KarmaLoop: Tracking Focus")
            .setContentText("Points: 0")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        startTrackingLoop()
        return START_STICKY
    }

    private fun startTrackingLoop() {
        val sharedPrefs = getSharedPreferences("KarmaPrefs", Context.MODE_PRIVATE)
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        runnable = object : Runnable {
            override fun run() {
                val time = System.currentTimeMillis()
                // Check last 5 seconds only (Fast detection)
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 5000, time)

                if (stats != null && stats.isNotEmpty()) {
                    // Find app with the latest timestamp
                    val topApp = stats.maxByOrNull { it.lastTimeUsed }

                    if (topApp != null) {
                        val currentPackageName = topApp.packageName

                        // 🛑 FIX: Agar app KarmaLoop hai, toh ignore karo aur return kar jao
                        if (currentPackageName != packageName) {

                            // Check karo is app ki category kya hai
                            val category = sharedPrefs.getString(currentPackageName, "None")

                            // Debugging ke liye Log lagaya hai
                            android.util.Log.d("KarmaLoop", "Active App: $currentPackageName | Category: $category")

                            when (category) {
                                "Hard" -> totalPoints += 10
                                "Mod" -> totalPoints += 5
                                "Easy" -> totalPoints += 1
                                "Dist" -> totalPoints -= 20
                            }

                            // Notification tabhi update hoga jab koi DOOSRA app khula ho
                            updateNotification("Points: $totalPoints | App: ${currentPackageName.takeLast(15)}")
                        }
                    }
                }
                // Har 2 second mein check karo (Faster updates)
                handler.postDelayed(this, 2000)
            }
        }
        runnable?.let { handler.post(it) }
    }

    private fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, "karma_channel")
            .setContentTitle("KarmaLoop: Tracking Focus")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("karma_channel", "Focus Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        runnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}