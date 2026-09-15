package com.mohdshayan.cropmark.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraState
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.check.LiveHint
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.ErrorPanel
import com.mohdshayan.cropmark.ui.components.MmRuler
import com.mohdshayan.cropmark.ui.components.PrimaryButton
import com.mohdshayan.cropmark.ui.components.SecondaryButton
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.components.SpecFrame
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.LocalReducedMotion
import com.mohdshayan.cropmark.ui.theme.PaperShape
import kotlinx.coroutines.launch

fun hintText(h: LiveHint): String = when (h) {
    LiveHint.NoFace -> "Find a face in the frame"
    LiveHint.TwoFaces -> "One person at a time"
    LiveHint.MoveCloser -> "Move closer"
    LiveHint.MoveBack -> "Move back"
    LiveHint.CenterFace -> "Center your face"
    LiveHint.RaisePhone -> "Raise the phone a little"
    LiveHint.LowerPhone -> "Lower the phone a little"
    LiveHint.TiltLevel -> "Tilt your head level"
    LiveHint.LookStraight -> "Look straight at the camera"
    LiveHint.Ready -> "Take photo"
}

private fun spokenHint(h: LiveHint): String = when (h) {
    LiveHint.MoveCloser -> "Head too small, move closer"
    LiveHint.MoveBack -> "Head too large, move back"
    LiveHint.Ready -> "Face fits the frame, take photo"
    else -> hintText(h)
}

@Composable
fun CaptureScreen(
    specId: String,
    onBack: () -> Unit,
    onCaptured: (Long) -> Unit,
    onGalleryPicked: (String) -> Unit,
    viewModel: CaptureViewModel = viewModel(),
) {
    viewModel.bind(specId)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    // After a second refusal Android stops showing the dialog, so the way back is the app's settings page.
    var blocked by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        blocked = !ok && context.findActivity()?.let {
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
        scope.launch { ServiceLocator.appPrefs.setCameraRationaleShown() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onGalleryPicked(uri.toString())
    }
    val openGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val view = LocalView.current
    val shoot = {
        viewModel.shutter(view.display?.rotation ?: 0, { id ->
            Toast.makeText(context, "Photo taken", Toast.LENGTH_SHORT).show()
            onCaptured(id)
        }) { Toast.makeText(context, "The photo was not taken. Try again.", Toast.LENGTH_SHORT).show() }
    }

    // Volume keys and remotes shoot only while this screen is resumed.
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by ServiceLocator.appPrefs.settings.collectAsStateWithLifecycle(initialValue = com.mohdshayan.cropmark.data.prefs.Settings())
    DisposableEffect(lifecycleOwner, granted, settings.volumeShutter) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // The camera may have been allowed in system settings while the app was away.
                    if (!granted && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        granted = true
                        denied = false
                    }
                    ServiceLocator.shutter.active = granted && settings.volumeShutter
                }
                Lifecycle.Event.ON_PAUSE -> ServiceLocator.shutter.active = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            ServiceLocator.shutter.active = granted && settings.volumeShutter
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ServiceLocator.shutter.active = false
        }
    }
    LaunchedEffect(granted) {
        if (granted) ServiceLocator.shutter.presses.collect { shoot() }
    }

    Scaffold(
        containerColor = c.backdrop,
        topBar = {
            CropmarkTopBar(ui.spec?.name ?: "Take photo", onBack, actions = {
                if (granted) {
                    IconButton(onClick = viewModel::cycleTimer) {
                        Icon(
                            if (ui.timerSeconds == 0) Icons.Outlined.TimerOff else Icons.Outlined.Timer,
                            contentDescription = if (ui.timerSeconds == 0) "Timer off" else "Timer ${ui.timerSeconds} seconds",
                        )
                    }
                    if (ui.timerSeconds > 0) Text("${ui.timerSeconds}s", style = MaterialTheme.typography.labelLarge, color = c.ink)
                    IconButton(onClick = viewModel::toggleLens) {
                        Icon(Icons.Outlined.Cameraswitch, contentDescription = if (ui.front) "Use back camera" else "Use front camera")
                    }
                }
            })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                !granted -> Column(
                    Modifier.widthIn(max = 480.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (denied) {
                        ErrorPanel(
                            "Cropmark needs the camera to take your photo.",
                            "You can still size a photo you already have, or allow the camera in system settings.",
                            primary = "Choose from gallery" to openGallery,
                            secondary = if (blocked) {
                                "Open settings" to {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        ),
                                    )
                                }
                            } else {
                                "Ask again" to { permission.launch(Manifest.permission.CAMERA) }
                            },
                        )
                    } else {
                        Text("Cropmark needs the camera to take your photo", style = MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Frames are checked on the phone to place the guides. Only the photo you take is saved, and it stays on this phone.",
                            style = MaterialTheme.typography.bodyLarge, color = c.slate, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        PrimaryButton("Allow camera", { permission.launch(Manifest.permission.CAMERA) })
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton("Choose from gallery", openGallery)
                    }
                }
                ui.problem != null -> ErrorPanel(
                    if (ui.problem == CameraProblem.InUse) "Another app is using the camera." else "No camera was found on this device.",
                    if (ui.problem == CameraProblem.InUse) "Close it and try again, or size a photo you already have." else "Size a photo you already have instead.",
                    Modifier.widthIn(max = 480.dp).padding(24.dp),
                    primary = "Try again" to viewModel::retry,
                    secondary = "Choose from gallery" to openGallery,
                )
                ui.spec == null -> SkeletonBlock(Modifier.fillMaxSize().padding(20.dp), PaperShape)
                else -> Column(Modifier.fillMaxSize()) {
                    Viewfinder(viewModel, ui, Modifier.weight(1f).fillMaxWidth())
                    Text(
                        if (ui.hint == LiveHint.NoFace) "Keep your whole head between the brackets" else spokenHint(ui.hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ui.hint == LiveHint.Ready) c.magenta else c.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = openGallery, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Choose from gallery", tint = c.ink)
                        }
                        val label = when {
                            ui.taking -> "Taking photo"
                            ui.countdown != null -> "Cancel timer"
                            else -> hintText(ui.hint)
                        }
                        PrimaryButton(label, shoot, Modifier.weight(1f).height(56.dp), enabled = ui.cameraReady && !ui.taking)
                    }
                }
            }
        }
    }
}

@Composable
private fun Viewfinder(vm: CaptureViewModel, ui: CaptureUi, modifier: Modifier) {
    val c = Cropmark.colors
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val reduced = LocalReducedMotion.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    BoxWithConstraints(modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val aspect = if (landscape) 4f / 3f else 3f / 4f
        val boxW = minOf(maxWidth, maxHeight * aspect)
        val boxH = boxW / aspect
        vm.setViewAspect(aspect)
        val frame = vm.frameRect(aspect) ?: return@BoxWithConstraints
        val spec = ui.spec ?: return@BoxWithConstraints

        LaunchedEffect(ui.front, ui.bindKey, owner) {
            try {
                val cam = vm.camera.bind(owner, previewView, ui.front) { count, face -> vm.onFrame(count, face) }
                cam.cameraInfo.cameraState.observe(owner) { state ->
                    when (state.error?.code) {
                        CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE -> vm.onProblem(CameraProblem.InUse)
                        CameraState.ERROR_CAMERA_DISABLED, CameraState.ERROR_CAMERA_FATAL_ERROR -> vm.onProblem(CameraProblem.InUse)
                        else -> if (state.type == CameraState.Type.OPEN) vm.onBound()
                    }
                }
            } catch (e: Exception) {
                vm.onProblem(if (vm.camera.hasLens(ui.front)) CameraProblem.InUse else CameraProblem.NoCamera)
            }
        }
        DisposableEffect(Unit) { onDispose { vm.camera.unbind() } }

        val target = ui.face?.let { FrameGuides.fromLive(it, frame, spec) } ?: FrameGuides.canonical(spec)
        val spec120 = if (reduced) snap<Float>() else tween(120)
        val crown by animateFloatAsState(target.crown, spec120, label = "crown")
        val chin by animateFloatAsState(target.chin, spec120, label = "chin")
        val mid by animateFloatAsState(target.mid, spec120, label = "mid")
        val half by animateFloatAsState(target.halfWidth, spec120, label = "half")
        // The one first-run moment: the first tracked face pulls the brackets in from the frame edges.
        val pull = remember { Animatable(if (ui.firstMoment && !reduced) 1f else 0f) }
        LaunchedEffect(ui.face != null) {
            if (ui.face != null && pull.value > 0f) pull.animateTo(0f, if (reduced) snap() else tween(350))
        }
        val p = pull.value
        val guides = target.copy(
            crown = crown * (1 - p),
            chin = chin + (1f - chin) * p,
            mid = mid + (0.5f - mid) * p,
            halfWidth = half + (0.5f - half) * p,
        )
        val scrim = c.backdrop.copy(alpha = 0.62f)

        Box(Modifier.size(boxW, boxH).background(c.panel)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (!ui.cameraReady) SkeletonBlock(Modifier.fillMaxSize(), PaperShape)
            Canvas(Modifier.fillMaxSize()) {
                val l = frame.left * size.width
                val t = frame.top * size.height
                val r = frame.right * size.width
                val b = frame.bottom * size.height
                clipRect(l, t, r, b, clipOp = ClipOp.Difference) { drawRect(scrim) }
                drawRect(c.slate, Offset(l, t), Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            SpecFrame(
                guides,
                Modifier
                    .offset(boxW * frame.left, boxH * frame.top)
                    .size(boxW * frame.width, boxH * frame.height),
                ready = ui.hint == LiveHint.Ready,
                description = spokenHint(ui.hint),
            )
            // The millimetre ruler hugs the frame's right edge, with the head range in magenta.
            val rulerW = minOf(34.dp, boxW * (1f - frame.right) - 4.dp)
            if (rulerW >= 18.dp) {
                MmRuler(
                    if (spec.print == null) 100f else spec.heightMm,
                    guides,
                    Modifier
                        .offset(boxW * frame.right + 4.dp, boxH * frame.top)
                        .size(rulerW, boxH * frame.height),
                    percent = spec.print == null,
                )
            }
            ui.countdown?.let { n ->
                Text(
                    "$n",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * 3),
                    color = c.magenta,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
