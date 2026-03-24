package com.video

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.delay

val BackgroundColor = Color(0xFF121212)
val CardColor = Color(0xFF1E1E1E)
val PrimaryOrange = Color(0xFFDA7757)

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private lateinit var viewModel: VideoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel = VideoViewModel(applicationContext)
        exoPlayer = ExoPlayer.Builder(this).build()
        setContent { MainApp(exoPlayer, viewModel) }
    }

    fun toggleSystemBars(show: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.let {
            if (show) it.show(WindowInsetsCompat.Type.systemBars())
            else {
                it.hide(WindowInsetsCompat.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}

@Composable
fun MainApp(player: ExoPlayer?, viewModel: VideoViewModel) {
    val context = LocalContext.current as MainActivity
    var currentPlayingUri by remember { mutableStateOf<Uri?>(null) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var currentScreen by remember { mutableStateOf("home") }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var videoToRename by remember { mutableStateOf<VideoModel?>(null) }
    var newNameText by remember { mutableStateOf("") }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { 
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            viewModel.addVideo(it, getFileName(context, it))
        }
    }

    // Permission check for Android 13+ (14, 15) and older versions
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) pickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        else Toast.makeText(context, "Access Denied! Check settings.", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(isControlsVisible, currentPlayingUri) {
        if (currentPlayingUri != null) {
            context.toggleSystemBars(isControlsVisible)
            if (isControlsVisible) { delay(4000); isControlsVisible = false }
        } else context.toggleSystemBars(true)
    }

    BackHandler(enabled = currentPlayingUri != null) {
        player?.stop()
        currentPlayingUri = null
        isControlsVisible = true
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        if (currentPlayingUri != null) {
            VideoPlayerScreen(player = player, onBack = { player?.stop(); currentPlayingUri = null; isControlsVisible = true; context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }, isVisible = isControlsVisible, onToggle = { isControlsVisible = !isControlsVisible })
        } else {
            MainScaffold(
                currentScreen = currentScreen,
                videoList = viewModel.videoList,
                onScreenChange = { currentScreen = it },
                onVideoClick = { video -> currentPlayingUri = video.uri; player?.apply { setMediaItem(MediaItem.fromUri(video.uri)); prepare(); play() } },
                onAddClick = { permissionLauncher.launch(permissionToRequest) },
                onDelete = { viewModel.deleteVideo(it) },
                onRename = { video -> videoToRename = video; newNameText = video.name; showRenameDialog = true }
            )
        }

        if (showRenameDialog) {
            RenameDialog(name = newNameText, onNameChange = { newNameText = it }, onDismiss = { showRenameDialog = false }, onConfirm = { videoToRename?.let { viewModel.renameVideo(it, newNameText) }; showRenameDialog = false })
        }
    }
}

@Composable
fun VideoPlayerScreen(player: ExoPlayer?, onBack: () -> Unit, isVisible: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current as MainActivity
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(isVisible, player?.isPlaying) {
        while (isVisible || player?.isPlaying == true) {
            currentTime = player?.currentPosition ?: 0L
            totalDuration = player?.duration ?: 0L
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onToggle() }) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())
        if (isVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f))) {
                IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(15.dp).align(Alignment.TopStart)) { Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    IconButton(onClick = { player?.seekBack() }) { Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(55.dp).background(Color.White.copy(0.15f), CircleShape).padding(12.dp)) }
                    IconButton(onClick = { if (player?.isPlaying == true) player.pause() else player?.play() }) { Icon(if (player?.isPlaying == true) Icons.Rounded.Close else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(85.dp).background(Color.White.copy(0.2f), CircleShape).padding(15.dp)) }
                    IconButton(onClick = { player?.seekForward() }) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(55.dp).background(Color.White.copy(0.15f), CircleShape).padding(12.dp)) }
                }
                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 20.dp, end = 20.dp)) {
                    Slider(value = if (totalDuration > 0) currentTime.toFloat() / totalDuration.toFloat() else 0f, onValueChange = { player?.seekTo((it * totalDuration).toLong()) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "${formatTime(currentTime)} / ${formatTime(totalDuration)}", color = Color.White, fontSize = 14.sp)
                        IconButton(onClick = { context.requestedOrientation = if (context.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }) { Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = if (ms > 0) ms / 1000 else 0
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun MainScaffold(currentScreen: String, videoList: List<VideoModel>, onScreenChange: (String) -> Unit, onVideoClick: (VideoModel) -> Unit, onAddClick: () -> Unit, onDelete: (VideoModel) -> Unit, onRename: (VideoModel) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        backgroundColor = BackgroundColor,
        bottomBar = { AppBottomNavigation(currentScreen, onScreenChange) },
        floatingActionButtonPosition = FabPosition.Center,
        isFloatingActionButtonDocked = true,
        floatingActionButton = { FloatingActionButton(onClick = onAddClick, backgroundColor = PrimaryOrange, shape = CircleShape) { Icon(Icons.Default.Add, null, tint = Color.White) } }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (currentScreen == "home") HomeScreen(videoList, onVideoClick, onDelete, onRename)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Settings Screen", color = Color.White) }
        }
    }
}

@Composable
fun HomeScreen(videoList: List<VideoModel>, onVideoClick: (VideoModel) -> Unit, onDelete: (VideoModel) -> Unit, onRename: (VideoModel) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My Library", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 25.dp, bottom = 15.dp))
        if (videoList.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No videos found", color = Color.Gray) }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(videoList) { video ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { onVideoClick(video) }, backgroundColor = CardColor, shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(video.uri).decoderFactory(VideoFrameDecoder.Factory()).build(), contentDescription = null, modifier = Modifier.size(100.dp, 60.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(15.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = video.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row {
                                    IconButton(onClick = { onRename(video) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                                    IconButton(onClick = { onDelete(video) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppBottomNavigation(currentScreen: String, onScreenChange: (String) -> Unit) {
    BottomNavigation(backgroundColor = CardColor, elevation = 12.dp) {
        // Home - Correct position for English (Left)
        BottomNavigationItem(
            selected = currentScreen == "home", 
            onClick = { onScreenChange("home") }, 
            icon = { Icon(Icons.Default.Home, null) }, 
            label = { Text("Home") }, 
            selectedContentColor = PrimaryOrange, 
            unselectedContentColor = Color.Gray
        )

        Spacer(Modifier.weight(1f))

        // Settings - Correct position for English (Right)
        BottomNavigationItem(
            selected = currentScreen == "settings", 
            onClick = { onScreenChange("settings") }, 
            icon = { Icon(Icons.Default.Settings, null) }, 
            label = { Text("Settings") }, 
            selectedContentColor = PrimaryOrange, 
            unselectedContentColor = Color.Gray
        )
    }
}

@Composable
fun RenameDialog(name: String, onNameChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, backgroundColor = CardColor, title = { Text("Rename", color = Color.White) },
        text = { TextField(value = name, onValueChange = onNameChange, colors = TextFieldDefaults.textFieldColors(textColor = Color.White)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save", color = PrimaryOrange) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } }
    )
}

//هذا كود