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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    // 📊 Points Tracking (Total for Notification)
    private var totalPoints = 0.0

    // 📦 Category Buffers (Alag-alag store karne ke liye)
    private var bufferHard = 0.0
    private var bufferMod = 0.0
    private var bufferEasy = 0.0
    private var bufferDist = 0.0

    private var dbPushCounter = 0

    // 🔥 Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, "karma_channel")
            .setContentTitle("KarmaLoop: Tracking Focus")
            .setContentText("Points: 0.0")
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
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 5000,
                    time
                )

                if (!stats.isNullOrEmpty()) {
                    val topApp = stats.maxByOrNull { it.lastTimeUsed }

                    topApp?.let {
                        val currentPackageName = it.packageName

                        if (currentPackageName != packageName) {
                            val category = sharedPrefs.getString(currentPackageName, "None")

                            // Category wise Logic
                            when (category) {
                                "Hard" -> {
                                    val pts = 1.0
                                    totalPoints += pts
                                    bufferHard += pts  // Sirf Hard wale bakse me daalo
                                }
                                "Mod" -> {
                                    val pts = 0.5
                                    totalPoints += pts
                                    bufferMod += pts
                                }
                                "Easy" -> {
                                    val pts = 0.25
                                    totalPoints += pts
                                    bufferEasy += pts
                                }
                                "Dist" -> {
                                    val pts = -0.125
                                    totalPoints += pts
                                    bufferDist += pts // Negative value jayegi
                                }
                            }

                            // Notification update (Total hi dikhayega user ko)
                            updateNotification(
                                "Points: ${"%.2f".format(totalPoints)} | App: ${
                                    getAppNameFromPackage(currentPackageName)
                                }"
                            )
                        }
                    }
                }

                // 🔄 Firebase push every ~10 sec
                dbPushCounter++
                if (dbPushCounter >= 5) {
                    pushToDatabase()
                    dbPushCounter = 0
                }

                handler.postDelayed(this, 2000)
            }
        }
        runnable?.let { handler.post(it) }
    }

    // 🔥 Firebase Push (Ab ye Categories bhejega)
    private fun pushToDatabase() {
        // Agar sabhi buffers 0 hain, toh network call mat karo
        if (bufferHard == 0.0 && bufferMod == 0.0 && bufferEasy == 0.0 && bufferDist == 0.0) return

        val uid = auth.currentUser?.uid ?: return

        // Values ko local variables me copy karo (Snapshot)
        val sendHard = bufferHard
        val sendMod = bufferMod
        val sendEasy = bufferEasy
        val sendDist = bufferDist

        // Buffers ko turant reset kar do
        bufferHard = 0.0
        bufferMod = 0.0
        bufferEasy = 0.0
        bufferDist = 0.0

        CoroutineScope(Dispatchers.IO).launch {
            // 📝 AB HUM MULTIPLE FIELDS BHEJ RAHE HAIN
            val updates = hashMapOf<String, Any>(
                "points_hard" to FieldValue.increment(sendHard),
                "points_mod" to FieldValue.increment(sendMod),
                "points_easy" to FieldValue.increment(sendEasy),
                "points_dist" to FieldValue.increment(sendDist),

                // Optional: Ek "Total" field bhi rakh sakte hain calculation ke liye
                "points_total" to FieldValue.increment(sendHard + sendMod + sendEasy + sendDist),

                "last_active" to System.currentTimeMillis()
            )

            db.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .addOnFailureListener {
                    // Agar fail hua, toh wapas buffer me jod do taaki data loss na ho
                    bufferHard += sendHard
                    bufferMod += sendMod
                    bufferEasy += sendEasy
                    bufferDist += sendDist
                }
        }
    }

    private fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, "karma_channel")
            .setContentTitle("KarmaLoop Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "karma_channel",
            "Focus Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        pushToDatabase()
        runnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}