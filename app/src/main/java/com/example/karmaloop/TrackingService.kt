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
import java.util.TreeMap

class TrackingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    // Buffers
    private var bufferHard = 0.0
    private var bufferMod = 0.0
    private var bufferEasy = 0.0
    private var bufferDist = 0.0

    // DB Counter
    private var dbPushCounter = 0

    // 🔥 Grind Mode State
    private var isGrindMode = false
    private var grindStartTime = 0L

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newGrindState = intent?.getBooleanExtra("GRIND_MODE", false) ?: false

        // Agar mode change ho raha hai ya first time start hai
        if (isGrindMode != newGrindState || grindStartTime == 0L) {
            isGrindMode = newGrindState
            if (isGrindMode) {
                grindStartTime = System.currentTimeMillis()
            }
        }

        createNotificationChannel()
        val notification = createNotification("Initializing KarmaLoop...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        startTrackingLoop()
        return START_STICKY
    }

    private fun startTrackingLoop() {
        // Purana runnable hatao taaki multiple loops na chalein
        runnable?.let { handler.removeCallbacks(it) }

        val sharedPrefs = getSharedPreferences("KarmaPrefs", Context.MODE_PRIVATE)
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        runnable = object : Runnable {
            override fun run() {
                if (isGrindMode) {
                    // 🚀 GRIND MODE LOGIC (Time Based)
                    val elapsedMinutes = (System.currentTimeMillis() - grindStartTime) / 60000

                    // Logic: Har 2 second loop chalta hai.
                    // Agar 60 min se upar hai, toh har 2 sec me 0.1 point (approx 3 points/min)
                    // Ye values adjust ki gayi hain taaki overflow na ho.
                    when {
                        elapsedMinutes >= 60 -> bufferHard += 0.1
                        elapsedMinutes >= 30 -> bufferMod += 0.05
                        else -> bufferEasy += 0.02
                    }

                    updateNotification("Grind Mode: ${elapsedMinutes}m elapsed | Focusing 🔥")
                } else {
                    // 📊 NORMAL APP TRACKING LOGIC
                    // FIX: Window size badha kar 10 seconds kiya (Battery Optimization fix)
                    val time = System.currentTimeMillis()
                    val stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        time - 1000 * 10, // Look back 10 seconds
                        time
                    )

                    if (!stats.isNullOrEmpty()) {
                        // FIX: SortedMap use karke latest app nikala (More Reliable)
                        val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                        val topApp = sortedStats.firstOrNull()

                        topApp?.let {
                            if (it.packageName != packageName) {
                                val category = sharedPrefs.getString(it.packageName, "None")

                                // Point Logic (Every 2 seconds)
                                when (category) {
                                    "Hard" -> bufferHard += 1.0
                                    "Mod" -> bufferMod += 0.5
                                    "Easy" -> bufferEasy += 0.25
                                    "Dist" -> bufferDist -= 0.125
                                }

                                if (category != "None") {
                                    updateNotification("Tracking: ${getAppNameFromPackage(it.packageName)} | pts: +${bufferHard+bufferMod+bufferEasy}")
                                }
                            }
                        }
                    }
                }

                // 🔄 Firebase push every ~10 sec (Buffer 5 tak)
                dbPushCounter++
                if (dbPushCounter >= 5) {
                    pushToDatabase()
                    dbPushCounter = 0
                }

                handler.postDelayed(this, 2000) // Loop every 2 seconds
            }
        }
        runnable?.let { handler.post(it) }
    }

    private fun pushToDatabase() {
        val uid = auth.currentUser?.uid ?: return
        // Agar koi points nahi hain to network call mat karo
        if (bufferHard == 0.0 && bufferMod == 0.0 && bufferEasy == 0.0 && bufferDist == 0.0) return

        // Local copy banao aur buffer turant clear karo (Race condition fix)
        val sHard = bufferHard; val sMod = bufferMod; val sEasy = bufferEasy; val sDist = bufferDist
        bufferHard = 0.0; bufferMod = 0.0; bufferEasy = 0.0; bufferDist = 0.0

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updates = hashMapOf<String, Any>(
                    "points_hard" to FieldValue.increment(sHard),
                    "points_mod" to FieldValue.increment(sMod),
                    "points_easy" to FieldValue.increment(sEasy),
                    "points_dist" to FieldValue.increment(sDist),
                    "points_total" to FieldValue.increment(sHard + sMod + sEasy + sDist),
                    "last_active" to System.currentTimeMillis()
                )

                db.collection("users").document(uid).set(updates, SetOptions.merge())
            } catch (e: Exception) {
                // Fail hone par wapas buffer me daal do
                bufferHard += sHard; bufferMod += sMod; bufferEasy += sEasy; bufferDist += sDist
            }
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "karma_channel")
            .setContentTitle("KarmaLoop Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Baar baar sound nahi bajega
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) { packageName }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("karma_channel", "Focus Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        // Service band hote hi final data push karo
        pushToDatabase()
        runnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}