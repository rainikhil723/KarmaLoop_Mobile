package com.example.karmaloop

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
// Google & Firebase Imports
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


// Data Models
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
                    // Background Image (As requested, logic preserved)
                    Image(
                        painter = painterResource(id = R.drawable.app_bg),
                        contentDescription = "Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Professional Dark Overlay
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
    val auth = FirebaseAuth.getInstance()

    // User Data track karein
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var currentScreen by remember { mutableStateOf(if (currentUser != null) "Home" else "Login") }

    if (!hasUsageStatsPermission(context)) {
        PermissionScreen(context)
    } else {
        AnimatedContent(targetState = currentScreen, label = "ScreenNav") { screen ->
            when (screen) {
                "Login" -> LoginScreen(onLoginSuccess = {
                    currentUser = auth.currentUser // 👈 Login hote hi data update hoga
                    currentScreen = "Home"
                })
                "Home" -> HomeScreen(
                    user = currentUser, // 👈 Hum user ka data Home ko bhej rahe hain
                    onNavigateToCategories = { currentScreen = "AppList" },
                    onNavigateToDev = { currentScreen = "Developers" },
                    onLogout = {
                        auth.signOut()
                        currentScreen = "Login"
                    }
                )
                "AppList" -> AppListScreen(onBack = { currentScreen = "Home" })
                "Developers" -> AboutDeveloperScreen(onBack = { currentScreen = "Home" })
            }
        }
    }
}

// =======================
// 🔐 SCREEN: LOGIN (NEW ADDITION)
// =======================

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Firestore ka instance
    val db = FirebaseFirestore.getInstance()

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            coroutineScope.launch {
                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val user = authResult.user

                // 🔥 DATABASE SAVE LOGIC (NEW)
                if (user != null) {
                    val userData = hashMapOf(
                        "name" to (user.displayName ?: "Unknown"),
                        "email" to (user.email ?: ""),
                        "uid" to user.uid,
                        "points" to 0, // Shuru mein 0 points
                        "lastLogin" to System.currentTimeMillis()
                    )

                    // User ko DB mein save karo (Merge true taaki purana data delete na ho)
                    db.collection("users").document(user.uid)
                        .set(userData, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }

                isLoading = false
                onLoginSuccess()
            }
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // UI CODE (Ye wahi purana hai, same rakhna)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White).border(2.dp, Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Sign in to sync your focus.", color = Color.White)
            Spacer(modifier = Modifier.height(40.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        launcher.launch(googleSignInClient.signInIntent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// =======================
// 🏠 SCREEN 1: HOME (UPDATED WITH LOGOUT)
// =======================
@Composable
fun HomeScreen(
    user: FirebaseUser?,
    onNavigateToCategories: () -> Unit,
    onNavigateToDev: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // NOTE: Points logic removed from UI as requested

    Box(modifier = Modifier.fillMaxSize()) {
        // 🔥 MAIN COLUMN (Scrollable & Optimized Spacing)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp) // Side padding only
                .verticalScroll(rememberScrollState()), // 👈 SCROLL ENABLED
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // 1. TOP SPACING (Reduced to move everything UP)
            Spacer(modifier = Modifier.height(24.dp))

            // 2. HEADER (Profile & Logout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Gray.copy(alpha = 0.8f), RoundedCornerShape(50.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user?.photoUrl != null) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.size(40.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, "Profile", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Welcome Back,", color = Color.White, fontSize = 12.sp)
                        Text(user?.displayName ?: "User", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.ExitToApp, "Logout", tint = Color(0xFFFF5252))
                }
            }

            // 3. LOGO SECTION (Gap Reduced: 40dp -> 20dp)
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(Color.White.copy(alpha=1f), Color.Transparent)))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                )
            }

            // 4. TITLE (Gap Reduced: 20dp -> 10dp)
            Spacer(modifier = Modifier.height(10.dp))

            GlassCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 30.dp)
                ) {
                    Text(
                        text = "KarmaLoop",
                        style = TextStyle(
                            fontSize = 32.sp, // Thoda chota kiya for better fit
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Serif,
                            shadow = Shadow(Color.Blue.copy(0.25f), Offset(4f, 4f), 8f)
                        ),
                        color = Color.Black
                    )
                    Text(
                        text = "FOCUS MASTERY",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            brush = Brush.linearGradient(listOf(Color(0xFF2D8EE2), Color(0xFF5FCFE3)))
                        )
                    )
                }
            }

            // 5. BUTTONS START (Gap Reduced: 50dp -> 30dp)
            Spacer(modifier = Modifier.height(30.dp))

            GradientButton("Start Focus Mode", Icons.Default.PlayArrow, Color(0xFF11998e), Color(0xFF38ef7d)) {
                val intent = Intent(context, TrackingService::class.java)
                context.startForegroundService(intent)
                Toast.makeText(context, "Tracker Started!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(16.dp))

            GradientButton("Manage Apps", Icons.Default.Settings, Color(0xFF4facfe), Color(0xFF00f2fe)) { onNavigateToCategories() }

            Spacer(modifier = Modifier.height(16.dp))

            // Visit Dashboard Button
            GradientButton("Visit Dashboard", Icons.Default.Info, Color(0xFF8E2DE2), Color(0xFF4A00E0)) {
                Toast.makeText(context, "Dashboard Website Coming Soon!", Toast.LENGTH_SHORT).show()
                val url = "https://karmaloop-94f77.web.app" // Future URL
                // context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }

            Spacer(modifier = Modifier.height(16.dp))

            GradientButton("Stop Tracking", Icons.Default.Close, Color(0xFFff9966), Color(0xFFff5e62)) {
                val intent = Intent(context, TrackingService::class.java)
                context.stopService(intent)
                Toast.makeText(context, "Stopped", Toast.LENGTH_SHORT).show()
            }

            // 🔥 BOTTOM CUSHION (Ye zaroori hai taaki last button chhupe nahi)
            Spacer(modifier = Modifier.height(100.dp))
        }

        // Floating Buttons (Z-Index High, they stay on top)
        FloatingActionButton(onClick = onNavigateToDev, containerColor = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(30.dp)) { Icon(Icons.Default.Person, "Dev", tint = Color.Black) }
        FloatingActionButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/7838758231"))) }, containerColor = Color(0xFF25D366), modifier = Modifier.align(Alignment.BottomEnd).padding(30.dp)) { Icon(Icons.Default.Phone, "Contact", tint = Color.White) }
    }
}
// =======================
// 📃 SCREEN 2: APP LIST
// =======================
@Composable
fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) } // Loading Indicator
    val sharedPrefs = context.getSharedPreferences("KarmaPrefs", Context.MODE_PRIVATE)
    val appCategories = remember { mutableStateMapOf<String, String>() }

    BackHandler { onBack() }

    fun resetCategories() {
        val editor = sharedPrefs.edit()
        installedApps.forEach { app ->
            editor.remove(app.packageName)
            appCategories.remove(app.packageName)
        }
        editor.apply()
        Toast.makeText(context, "All categories reset", Toast.LENGTH_SHORT).show()
    }

    // 🔥 Load Apps in Background (No Lag)
    LaunchedEffect(Unit) {
        isLoading = true
        installedApps = getInstalledApps(context) // Calls the optimized function
        installedApps.forEach { app ->
            val savedCat = sharedPrefs.getString(app.packageName, "None")
            if (savedCat != null) appCategories[app.packageName] = savedCat
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            IconButton(onClick = { resetCategories() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.Red)
            }
        }

        if (isLoading) {
            // Loading Spinner jab tak apps load na ho
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Black)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(installedApps) { index, app ->
                    // Animation hata di thodi speed badhane ke liye
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
                    R.drawable.nikhil // Keeps original ID
                )
            )

            Spacer(modifier = Modifier.height(26.dp))

            FlipCard(
                developer = Developer(
                    "Chandra Shekhar Singh", "Full Stack Developer",
                    "React • Firebase • Authentication • Databases",
                    "Crafting seamless user experiences.",
                    R.drawable.shekhar // Keeps original ID
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
            .background(Color.White.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

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

// 👇 IS FUNCTION KO PURA REPLACE KAR DO (OPTIMIZED VERSION)
suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null)
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

    // Sirf wahi apps layega jo Menu me dikhte hain (No System junk)
    val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)

    val userApps = mutableListOf<AppInfo>()

    for (info in resolvedInfos) {
        val packageName = info.activityInfo.packageName

        // Khud ki app ko list me mat dikhao
        if (packageName == context.packageName) continue

        val label = info.loadLabel(pm).toString()
        val icon = info.loadIcon(pm)

        userApps.add(AppInfo(label, packageName, icon))
    }
    // Alphabetical order me sort karo
    userApps.sortedBy { it.name }
}