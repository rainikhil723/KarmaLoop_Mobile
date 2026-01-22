package com.example.karmaloop

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable // Needed for App Icons
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView // Needed for rendering App Icons
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView // Needed for App Icons
import androidx.core.content.ContextCompat

// Data Models (Updated to hold the real App Icon)
data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
data class Developer(val name: String, val role: String, val skills: String, val desc: String, val imageRes: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MaterialTheme {
                // GLOBAL BACKGROUND
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background Image
                    Image(
                        painter = painterResource(id = R.drawable.app_bg),
                        contentDescription = "Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Professional Dark Overlay (Gradient for better look)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

                    KarmaLoopApp()
                }
            }
        }
    }
}

@Composable
fun KarmaLoopApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("Home") }

    if (!hasUsageStatsPermission(context)) {
        PermissionScreen(context)
    } else {
        // Crossfade Animation between screens
        AnimatedContent(targetState = currentScreen, label = "ScreenNav") { screen ->
            when (screen) {
                "Home" -> HomeScreen(
                    onNavigateToCategories = { currentScreen = "AppList" },
                    onNavigateToDev = { currentScreen = "Developers" }
                )
                "AppList" -> AppListScreen(onBack = { currentScreen = "Home" })
                "Developers" -> AboutDeveloperScreen(onBack = { currentScreen = "Home" })
            }
        }
    }
}

// =======================
// 🏠 SCREEN 1: HOME
// =======================
@Composable
fun HomeScreen(onNavigateToCategories: () -> Unit, onNavigateToDev: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            // --- LOGO (Professional Glow) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha=0.3f), Color.Transparent)
                        )
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(110.dp).clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- PROFESSIONAL TEXT TYPOGRAPHY ---
            GlassCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 40.dp)
                ) {
                    Text(
                        text = "KarmaLoop",
                        style = TextStyle(
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Serif,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.25f),
                                offset = Offset(4f, 4f),
                                blurRadius = 8f
                            )
                        ),
                        color = Color.Black
                    )

                    // Golden Gradient Text for Subtitle
                    Text(
                        text = "FOCUS MASTERY",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)) // Deep Purple/Blue gradient
                            )
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Professional Gradient Buttons
            GradientButton(text = "Start Focus Mode", icon = Icons.Default.PlayArrow, color1 = Color(0xFF11998e), color2 = Color(0xFF38ef7d)) {
                val intent = Intent(context, TrackingService::class.java)
                context.startForegroundService(intent)
                Toast.makeText(context, "Tracker Started!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(20.dp))

            GradientButton(text = "Manage Apps", icon = Icons.Default.Settings, color1 = Color(0xFF4facfe), color2 = Color(0xFF00f2fe)) {
                onNavigateToCategories()
            }

            Spacer(modifier = Modifier.height(20.dp))

            GradientButton(text = "Stop Tracking", icon = Icons.Default.Close, color1 = Color(0xFFff9966), color2 = Color(0xFFff5e62)) {
                val intent = Intent(context, TrackingService::class.java)
                context.stopService(intent)
                Toast.makeText(context, "Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Floating Icons
        FloatingActionButton(
            onClick = onNavigateToDev,
            containerColor = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(30.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = "Dev", tint = Color.Black)
        }

        FloatingActionButton(
            onClick = {
                val url = "https://wa.me/7838758231"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            containerColor = Color(0xFF25D366),
            modifier = Modifier.align(Alignment.BottomEnd).padding(30.dp)
        ) {
            Icon(Icons.Default.Phone, contentDescription = "Contact", tint = Color.White)
        }
    }
}

// =======================
// 📃 SCREEN 2: APP LIST (WITH REAL ICONS & RESET)
// =======================
@Composable
fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    val sharedPrefs = context.getSharedPreferences("KarmaPrefs", Context.MODE_PRIVATE)
    val appCategories = remember { mutableStateMapOf<String, String>() }

    BackHandler { onBack() }

    // Logic to reset all categories
    fun resetCategories() {
        // Clear logic
        val editor = sharedPrefs.edit()
        installedApps.forEach { app ->
            editor.remove(app.packageName)
            appCategories.remove(app.packageName)
        }
        editor.apply()
        Toast.makeText(context, "All categories reset", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        installedApps = getInstalledApps(context)
        installedApps.forEach { app ->
            val savedCat = sharedPrefs.getString(app.packageName, "None")
            if (savedCat != null) appCategories[app.packageName] = savedCat
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.clickable { onBack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text("Manage Categories", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            // RESET BUTTON
            IconButton(onClick = { resetCategories() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.Red)
            }
        }

        // Staggered Animation List
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            itemsIndexed(installedApps) { index, app ->
                StaggeredEntry(delay = index * 30) { // Faster animation
                    AppRowWidget(
                        app = app,
                        currentCategory = appCategories[app.packageName] ?: "None",
                        onCategorySelected = { category ->
                            appCategories[app.packageName] = category
                            sharedPrefs.edit().putString(app.packageName, category).apply()
                        }
                    )
                }
            }
        }
    }
}

// =======================
// 👨‍💻 SCREEN 3: DEVELOPERS
// =======================
@Composable
fun AboutDeveloperScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha=0.9f)).padding(20.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.clickable { onBack() })
            Spacer(modifier = Modifier.width(16.dp))
            Text("Meet the Team", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            FlipCard(
                developer = Developer(
                    "Nikhil Rai", "Lead Developer",
                    "Android • React • Firebase • Authentication • DB",
                    "Building tools to help students reclaim their focus.",
                    R.drawable.nikhil
                )
            )

            Spacer(modifier = Modifier.height(26.dp))

            FlipCard(
                developer = Developer(
                    "Chandra Shekhar Singh", "Full Stack Developer",
                    "React • Firebase • Authentication • Databases",
                    "Crafting seamless user experiences.",
                    R.drawable.shekhar
                )
            )

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

// =======================
// 🎨 ANIMATED COMPONENTS
// =======================

@Composable
fun FlipCard(developer: Developer) {
    var rotated by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "flip"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable { rotated = !rotated }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
    ) {
        if (rotation <= 90f) {
            // FRONT FACE
            GlassCard(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = developer.imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(100.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(developer.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(developer.role, fontSize = 16.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tap to flip", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            // BACK FACE
            GlassCard(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Skills", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1565C0))
                    Text(developer.skills, textAlign = TextAlign.Center, lineHeight = 24.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("About", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1565C0))
                    Text(developer.desc, textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun AppRowWidget(app: AppInfo, currentCategory: String, onCategorySelected: (String) -> Unit) {
    // Control Center Style Widget
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // REAL APP ICON RENDERING
            AndroidView(
                modifier = Modifier.size(45.dp),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(app.icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Status: $currentCategory", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CategoryOption("Hard", Color(0xFF2E7D32), currentCategory, onCategorySelected)
            CategoryOption("Mod", Color(0xFFF9A825), currentCategory, onCategorySelected)
            CategoryOption("Dist", Color(0xFFC62828), currentCategory, onCategorySelected)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun CategoryOption(label: String, color: Color, currentCategory: String, onClick: (String) -> Unit) {
    val isSelected = currentCategory == label
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable { onClick(label) }
            .padding(vertical = 8.dp, horizontal = 20.dp)
            .then(if(isSelected) Modifier.shadow(2.dp, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(isSelected) color else Color.Gray)
    }
}

@Composable
fun StaggeredEntry(delay: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn()
    ) {
        content()
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector, color1: Color, color2: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(color1, color2))),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon Bubble
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha=0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.85f)) // Slightly clearer for professional look
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

// Permissions Helper
@Composable
fun PermissionScreen(context: Context) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        GlassCard {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Permission Needed", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Grant Access")
                }
            }
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val userApps = mutableListOf<AppInfo>()
    for (app in apps) {
        if (app.packageName == context.packageName) continue
        val intent = pm.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            // Load Real App Icon
            val icon = app.loadIcon(pm)
            userApps.add(AppInfo(app.loadLabel(pm).toString(), app.packageName, icon))
        }
    }
    return userApps.sortedBy { it.name }
}