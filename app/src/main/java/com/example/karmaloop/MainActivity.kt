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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
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

        // Permission Request for Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background Image
                    Image(
                        painter = painterResource(id = R.drawable.app_bg),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Dark Overlay
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.7f)))))

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

    // Google Sign In Client
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var currentScreen by remember { mutableStateOf(if (currentUser != null) "Home" else "Login") }

    // Check Usage Stats Permission
    if (!hasUsageStatsPermission(context)) {
        PermissionScreen(context)
    } else {
        AnimatedContent(targetState = currentScreen, label = "ScreenNav") { screen ->
            when (screen) {
                "Login" -> LoginScreen(googleSignInClient = googleSignInClient) {
                    currentUser = auth.currentUser
                    currentScreen = "Home"
                }
                "Home" -> HomeScreen(
                    user = currentUser,
                    onNavigateToCategories = { currentScreen = "AppList" },
                    onNavigateToDev = { currentScreen = "Developers" },
                    onLogout = {
                        auth.signOut()
                        googleSignInClient.signOut().addOnCompleteListener {
                            currentUser = null
                            currentScreen = "Login"
                        }
                    }
                )
                "AppList" -> AppListScreen(onBack = { currentScreen = "Home" })
                "Developers" -> AboutDeveloperScreen(onBack = { currentScreen = "Home" })
            }
        }
    }
}

@Composable
fun LoginScreen(googleSignInClient: GoogleSignInClient, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val db = FirebaseFirestore.getInstance()

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            coroutineScope.launch {
                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    val userData = hashMapOf("name" to (user.displayName ?: "Unknown"), "email" to (user.email ?: ""), "uid" to user.uid, "lastLogin" to System.currentTimeMillis())
                    db.collection("users").document(user.uid).set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
                }
                isLoading = false
                onLoginSuccess()
            }
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Login Failed", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White))
            Spacer(modifier = Modifier.height(20.dp))
            Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(40.dp))
            if (isLoading) CircularProgressIndicator(color = Color.White) else Button(onClick = { isLoading = true; launcher.launch(googleSignInClient.signInIntent) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(25.dp)) { Text("Sign in with Google", color = Color.Black) }
        }
    }
}

@Composable
fun HomeScreen(user: FirebaseUser?, onNavigateToCategories: () -> Unit, onNavigateToDev: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    var isGrinding by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.8f), RoundedCornerShape(50.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = user?.photoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Welcome Back,", color = Color.White, fontSize = 12.sp)
                        Text(user?.displayName ?: "User", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Logout", tint = Color(0xFFFF5252)) }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(140.dp).clip(CircleShape).background(Color.White))
            Spacer(modifier = Modifier.height(30.dp))

            // GRIND MODE BUTTON
            GradientButton(text = if (isGrinding) "Stop Grind Mode" else "Start Grind Mode (Offline)", icon = if (isGrinding) Icons.Default.Close else Icons.Default.Star, color1 = if (isGrinding) Color(0xFFcb2d3e) else Color(0xFFfbc2eb), color2 = if (isGrinding) Color(0xFFef473a) else Color(0xFFa6c1ee)) {
                isGrinding = !isGrinding
                val intent = Intent(context, TrackingService::class.java).apply { putExtra("GRIND_MODE", isGrinding) }
                // If starting grind, start service. If stopping, stop service.
                if (isGrinding) {
                    context.startForegroundService(intent)
                    Toast.makeText(context, "Grind Mode Started!", Toast.LENGTH_SHORT).show()
                } else {
                    context.stopService(intent)
                    Toast.makeText(context, "Grind Mode Stopped", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NORMAL FOCUS MODE BUTTON
            GradientButton("Start Focus Mode", Icons.Default.PlayArrow, Color(0xFF11998e), Color(0xFF38ef7d)) {
                val intent = Intent(context, TrackingService::class.java).apply { putExtra("GRIND_MODE", false) }
                context.startForegroundService(intent)
                Toast.makeText(context, "Focus Tracking Started", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(16.dp))
            GradientButton("Manage Apps", Icons.Default.Settings, Color(0xFF4facfe), Color(0xFF00f2fe)) { onNavigateToCategories() }

            Spacer(modifier = Modifier.height(16.dp))
            GradientButton("Visit Dashboard", Icons.Default.Info, Color(0xFF8E2DE2), Color(0xFF4A00E0)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://karmaloop-94f77.web.app")))
            }

            Spacer(modifier = Modifier.height(16.dp))
            GradientButton("Stop Tracking", Icons.Default.Close, Color(0xFFff9966), Color(0xFFff5e62)) {
                context.stopService(Intent(context, TrackingService::class.java))
                isGrinding = false
                Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(120.dp))
        }

        // Floating Buttons
        FloatingActionButton(onClick = onNavigateToDev, containerColor = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(30.dp)) { Icon(Icons.Default.Person, "Dev", tint = Color.Black) }
        FloatingActionButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/7838758231"))) }, containerColor = Color(0xFF25D366), modifier = Modifier.align(Alignment.BottomEnd).padding(30.dp)) { Icon(Icons.Default.Phone, "Contact", tint = Color.White) }
    }
}

@Composable
fun PermissionScreen(context: Context) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
            Text("Grant Usage Permission")
        }
    }
}

@Composable
fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) }
    val sharedPrefs = context.getSharedPreferences("KarmaPrefs", Context.MODE_PRIVATE)
    val appCategories = remember { mutableStateMapOf<String, String>() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        isLoading = true
        installedApps = getInstalledApps(context)
        installedApps.forEach { app -> appCategories[app.packageName] = sharedPrefs.getString(app.packageName, "None") ?: "None" }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // FIXED: Header with RESET BUTTON
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.9f)).padding(20.dp).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.clickable { onBack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text("Manage Categories", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            // 🔥 Reset Button Added Here
            IconButton(onClick = {
                sharedPrefs.edit().clear().apply() // Clear Storage
                installedApps.forEach { appCategories[it.packageName] = "None" } // Clear UI
                Toast.makeText(context, "All categories reset!", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.Red)
            }
        }

        if (isLoading) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.Black) } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(installedApps) { _, app ->
                    AppRowWidget(app, appCategories[app.packageName] ?: "None") {
                        appCategories[app.packageName] = it
                        sharedPrefs.edit().putString(app.packageName, it).apply()
                    }
                }
            }
        }
    }
}

@Composable
fun AboutDeveloperScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha=0.9f)).padding(20.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.clickable { onBack() })
            Spacer(modifier = Modifier.width(16.dp))
            Text("Meet the Team", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            FlipCard(Developer("Nikhil Rai", "Lead Developer", "Android • React • Firebase", "Focusing on student tools.", R.drawable.nikhil))
            Spacer(modifier = Modifier.height(20.dp))
            FlipCard(Developer("Chandra Shekhar Singh", "Full Stack", "React • Firebase", "UI/UX Specialist.", R.drawable.shekhar))
        }
    }
}

@Composable
fun FlipCard(developer: Developer) {
    var rotated by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (rotated) 180f else 0f, animationSpec = tween(500), label = "flip")
    Box(modifier = Modifier.fillMaxWidth().height(250.dp).clickable { rotated = !rotated }.graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }) {
        if (rotation <= 90f) GlassCard(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Image(painterResource(developer.imageRes), null, Modifier.size(100.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape), contentScale = ContentScale.Crop)
                Text(developer.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(developer.role, color = Color.DarkGray)
            }
        } else GlassCard(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Skills", fontWeight = FontWeight.Bold); Text(developer.skills, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp)); Text(developer.desc, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun AppRowWidget(app: AppInfo, currentCategory: String, onCategorySelected: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(modifier = Modifier.size(40.dp), factory = { ctx -> ImageView(ctx).apply { setImageDrawable(app.icon) } })
            Spacer(modifier = Modifier.width(12.dp)); Text(app.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Hard", "Mod", "Dist").forEach { label ->
                val selected = currentCategory == label
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Color.DarkGray else Color.Transparent).clickable { onCategorySelected(label) }.padding(8.dp)) { Text(label, color = if (selected) Color.White else Color.Black, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector, color1: Color, color2: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(), modifier = Modifier.fillMaxWidth().height(55.dp).shadow(4.dp, RoundedCornerShape(15.dp)), shape = RoundedCornerShape(15.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(color1, color2))), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(modifier = Modifier.width(10.dp)); Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.8f)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))) { content() }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    pm.queryIntentActivities(intent, 0).map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }.sortedBy { it.name }
}