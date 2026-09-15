package com.mohdshayan.cropmark.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mohdshayan.cropmark.core.check.Guide
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.LocalReducedMotion

/**
 * The solved photo at its document proportions with the spec frame on top and the millimetre
 * ruler hugging its right edge. Fits the largest photo the space allows.
 */
@Composable
fun PhotoWithFrame(
    spec: DocSpec,
    preview: Bitmap?,
    guides: FrameGuides?,
    modifier: Modifier = Modifier,
    highlight: Guide = Guide.None,
    fade: Boolean = false,
    photoModifier: Modifier = Modifier,
    description: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val rulerW = 34.dp
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxPhotoW = maxWidth - rulerW - 6.dp
        val hFromW = maxPhotoW / spec.aspect
        val photoH = min(maxHeight, hFromW)
        val photoW = photoH * spec.aspect
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                photoModifier
                    .width(photoW)
                    .height(photoH)
                    .background(Cropmark.colors.panel),
            ) {
                if (preview != null) {
                    Crossfade(
                        targetState = preview,
                        animationSpec = if (reduced || !fade) snap() else tween(150),
                        label = "background",
                    ) { bmp ->
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = description,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    SkeletonBlock(Modifier.fillMaxSize(), com.mohdshayan.cropmark.ui.theme.PaperShape)
                }
                if (guides != null) SpecFrame(guides, Modifier.fillMaxSize(), highlight = highlight)
            }
            Spacer(Modifier.width(6.dp))
            MmRuler(if (spec.print == null) 100f else spec.heightMm, guides, Modifier.width(rulerW).height(photoH), percent = spec.print == null)
        }
    }
}
