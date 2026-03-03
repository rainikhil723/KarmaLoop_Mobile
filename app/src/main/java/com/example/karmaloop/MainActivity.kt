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
import androidx.compose.ui.draw.blur

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
data class Developer(val name: String, val role: String, val skills: String, val desc: String, val imageRes: Int)

val Obsidian = Color(0xFF0A0A0A)
val Charcoal = Color(0xFF121212)
val NeonCyan = Color(0xFF00F0FF)
val AccentPurple = Color(0xFFC084FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1A0B2E),
                                    Obsidian
                                )
                            )
                        )
                ) {
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

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var currentScreen by remember { mutableStateOf(if (currentUser != null) "Home" else "Login") }

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
            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape).border(2.dp, NeonCyan, CircleShape))
            Spacer(modifier = Modifier.height(20.dp))
            Text("BEGIN WITH", fontSize = 14.sp, color = NeonCyan, letterSpacing = 4.sp)
            Text("KARMALOOP", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(40.dp))
            if (isLoading) {
                CircularProgressIndicator(color = NeonCyan)
            } else {
                Button(
                    onClick = { isLoading = true; launcher.launch(googleSignInClient.signInIntent) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(25.dp))
                ) {
                    Text("Initialize Session", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
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

            Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(50.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = user?.photoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, NeonCyan, CircleShape), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Active User".uppercase(), color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
                        Text(user?.displayName ?: "Unknown", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Logout", tint = Color.Gray) }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(AccentPurple.copy(alpha = 0.2f)).blur(30.dp))
                Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(140.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            }
            Spacer(modifier = Modifier.height(50.dp))

            GradientButton(text = if (isGrinding) "TERMINATE GRIND" else "INITIALIZE GRIND MODE", icon = if (isGrinding) Icons.Default.Close else Icons.Default.Star, color1 = if (isGrinding) Color(0xFFcb2d3e) else AccentPurple, color2 = if (isGrinding) Color(0xFFef473a) else NeonCyan) {
                isGrinding = !isGrinding
                val intent = Intent(context, TrackingService::class.java).apply { putExtra("GRIND_MODE", isGrinding) }
                if (isGrinding) {
                    context.startForegroundService(intent)
                    Toast.makeText(context, "System Active", Toast.LENGTH_SHORT).show()
                } else {
                    context.stopService(intent)
                    Toast.makeText(context, "System Terminated", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GradientButton("STANDARD FOCUS", Icons.Default.PlayArrow, Charcoal, Charcoal, borderColor = NeonCyan) {
                val intent = Intent(context, TrackingService::class.java).apply { putExtra("GRIND_MODE", false) }
                context.startForegroundService(intent)
                Toast.makeText(context, "Focus Tracking Online", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(16.dp))
            GradientButton("CONFIGURE MATRIX", Icons.Default.Settings, Charcoal, Charcoal, borderColor = Color.White.copy(alpha = 0.3f)) { onNavigateToCategories() }
            Spacer(modifier = Modifier.height(12.dp))
            GradientButton("ACCESS DASHBOARD", Icons.Default.Info, Charcoal, Charcoal, borderColor = AccentPurple) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://karmaloop-94f77.web.app")))
            }
            Spacer(modifier = Modifier.height(12.dp))

            GradientButton("TERMINATE TRACKING", Icons.Default.Close, Charcoal, Charcoal, borderColor = Color(0xFFFF5252)) {
                context.stopService(Intent(context, TrackingService::class.java))
                isGrinding = false
                Toast.makeText(context, "Tracking Terminated", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(120.dp))
        }

        FloatingActionButton(onClick = onNavigateToDev, containerColor = Charcoal, modifier = Modifier.align(Alignment.BottomStart).padding(30.dp).border(1.dp, NeonCyan.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.Default.Person, "Dev", tint = NeonCyan) }
        FloatingActionButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/7838758231"))) }, containerColor = Charcoal, modifier = Modifier.align(Alignment.BottomEnd).padding(30.dp).border(1.dp, AccentPurple.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.Default.Phone, "Contact", tint = AccentPurple) }
    }
}

@Composable
fun PermissionScreen(context: Context) {
    Box(modifier = Modifier.fillMaxSize().background(Obsidian), contentAlignment = Alignment.Center) {
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Charcoal), modifier = Modifier.border(1.dp, NeonCyan, RoundedCornerShape(25.dp))) {
            Text("Grant Usage Permission", color = NeonCyan)
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

    Column(modifier = Modifier.fillMaxSize().background(Obsidian)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Charcoal).padding(20.dp).statusBarsPadding().border(1.dp, Color.White.copy(alpha = 0.05f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.clickable { onBack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text("MATRIX CONFIG", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
            }
            IconButton(onClick = {
                sharedPrefs.edit().clear().apply()
                installedApps.forEach { appCategories[it.packageName] = "None" }
                Toast.makeText(context, "Matrix Reset", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = NeonCyan)
            }
        }

        if (isLoading) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = NeonCyan) } else {
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
    Column(modifier = Modifier.fillMaxSize().background(Obsidian)) {
        Row(modifier = Modifier.fillMaxWidth().background(Charcoal).padding(20.dp).statusBarsPadding().border(1.dp, Color.White.copy(alpha = 0.05f)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.clickable { onBack() })
            Spacer(modifier = Modifier.width(16.dp))
            Text("THE ARCHITECTS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
        }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            FlipCard(Developer("Nikhil Rai", "Software Developer & Problem Solver", "Android • React • Firebase • CP", "A 20-year-old IT undergrad at USICT, Delhi. Blending full-stack development experience with competitive programming logic to architect KarmaLoop's core infrastructure.", R.drawable.nikhil))
            Spacer(modifier = Modifier.height(24.dp))
            FlipCard(Developer("Chandrashekhar Singh", "Product Engineer & Strategist", "React • Firebase • UI/UX", "Driving the product vision and translating complex survey data into actionable software features. Engineered the analytics engine powering the platform.", R.drawable.shekhar))
        }
    }
}

@Composable
fun FlipCard(developer: Developer) {
    var rotated by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (rotated) 180f else 0f, animationSpec = tween(500), label = "flip")
    Box(modifier = Modifier.fillMaxWidth().height(260.dp).clickable { rotated = !rotated }.graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }) {
        if (rotation <= 90f) GlassCard(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Image(painterResource(developer.imageRes), null, Modifier.size(100.dp).clip(CircleShape).border(2.dp, NeonCyan.copy(alpha = 0.5f), CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(16.dp))
                Text(developer.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(developer.role.uppercase(), color = AccentPurple, fontSize = 12.sp, letterSpacing = 1.sp)
            }
        } else GlassCard(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("CORE PROTOCOLS", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text(developer.skills, textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(developer.desc, textAlign = TextAlign.Center, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AppRowWidget(app: AppInfo, currentCategory: String, onCategorySelected: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), factory = { ctx -> ImageView(ctx).apply { setImageDrawable(app.icon) } })
            Spacer(modifier = Modifier.width(12.dp))
            Text(app.name, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Hard", "Mod", "Dist").forEach { label ->
                val selected = currentCategory == label
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) AccentPurple.copy(alpha = 0.2f) else Color.Transparent).border(1.dp, if (selected) AccentPurple else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable { onCategorySelected(label) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(label, color = if (selected) NeonCyan else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector, color1: Color, color2: Color, borderColor: Color = Color.Transparent, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(), modifier = Modifier.fillMaxWidth().height(55.dp).border(1.dp, borderColor, RoundedCornerShape(15.dp)).shadow(if (borderColor == Color.Transparent) 8.dp else 0.dp, RoundedCornerShape(15.dp)), shape = RoundedCornerShape(15.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(color1, color2))), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(modifier = Modifier.width(10.dp)); Text(text, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.03f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))) { content() }
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