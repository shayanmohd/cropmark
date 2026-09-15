package com.mohdshayan.cropmark.core.jpeg

/** Encodes the rendered photo at a pixel width and JPEG quality. The height follows the spec aspect. */
fun interface JpegEncoder {
    fun encode(widthPx: Int, heightPx: Int, quality: Int): ByteArray
}

sealed class SizeOutcome {
    data class Fits(val widthPx: Int, val heightPx: Int, val quality: Int, val bytes: ByteArray) : SizeOutcome() {
        override fun equals(other: Any?): Boolean =
            other is Fits && other.widthPx == widthPx && other.heightPx == heightPx && other.quality == quality && other.bytes.size == bytes.size
        override fun hashCode(): Int = (widthPx * 31 + heightPx) * 31 + quality
    }

    /** Even the lowest quality at the smallest allowed size is over the cap. */
    data class TooLarge(val widthPx: Int, val heightPx: Int, val smallestBytes: Int, val maxBytes: Int) : SizeOutcome()

    /** Even the best quality at the largest allowed size is under the minimum. */
    data class TooSmall(val widthPx: Int, val heightPx: Int, val largestBytes: Int, val minBytes: Int) : SizeOutcome()
}

/**
 * Finds the best JPEG that lands inside a form's file size limits. Quality is searched from high to
 * low under the cap; when the file is under a minimum, the pixel size rises first (a bigger photo
 * is better than a bloated one), and only a photo that stays too small at the largest size fails.
 */
object SizeSearch {
    const val MIN_QUALITY = 35
    const val MAX_QUALITY = 95

    /** Forms count a kilobyte loosely; caps use 1000 bytes and minimums 1024 so both sides are safe. */
    fun maxBytes(kb: Int?): Int? = kb?.let { it * 1000 }
    fun minBytes(kb: Int?): Int? = kb?.let { it * 1024 }

    fun search(
        requestedWidth: Int,
        minWidth: Int,
        maxWidth: Int,
        heightFor: (Int) -> Int,
        minBytes: Int?,
        maxBytes: Int?,
        encoder: JpegEncoder,
        maxQuality: Int = MAX_QUALITY,
        /**
         * The largest compression ratio allowed, against 3 bytes per pixel uncompressed. A file
         * squeezed past it under the cap is retried at a smaller width, where each pixel gets more bytes.
         */
        maxCompression: Int? = null,
    ): SizeOutcome {
        val topQuality = maxQuality.coerceIn(MIN_QUALITY, 100)
        val start = requestedWidth.coerceIn(minWidth, maxWidth)
        var width = start
        var raised = false
        var lowered = false
        var lastFits: SizeOutcome.Fits? = null
        var firstFits: SizeOutcome.Fits? = null
        while (true) {
            val height = heightFor(width)
            val best = bestUnderCap(width, height, maxBytes, encoder, topQuality)
            if (best == null) {
                // Raising the size to meet a minimum overshot the cap: the last size that fitted wins.
                if (raised) return lastFits ?: SizeOutcome.TooLarge(width, height, encoder.encode(width, height, MIN_QUALITY).size, maxBytes ?: 0)
                if (width > minWidth) {
                    lowered = true
                    width = maxOf(minWidth, width - stepFor(minWidth, maxWidth))
                    continue
                }
                val smallest = encoder.encode(width, height, MIN_QUALITY)
                return SizeOutcome.TooLarge(width, height, smallest.size, maxBytes ?: 0)
            }
            if (minBytes != null && best.bytes.size < minBytes) {
                if (width < maxWidth && !lowered) {
                    raised = true
                    lastFits = null
                    width = minOf(maxWidth, width + stepFor(minWidth, maxWidth))
                    continue
                }
                return SizeOutcome.TooSmall(width, height, best.bytes.size, minBytes)
            }
            if (maxCompression != null && !withinCompression(best, maxCompression) && width > minWidth && !raised) {
                if (firstFits == null) firstFits = best
                lowered = true
                width = maxOf(minWidth, width - stepFor(minWidth, maxWidth))
                continue
            }
            // Nothing smaller meets the ratio either: the requested width at its best quality stands.
            if (maxCompression != null && !withinCompression(best, maxCompression)) return firstFits ?: best
            return best
        }
    }

    fun withinCompression(f: SizeOutcome.Fits, maxCompression: Int): Boolean =
        f.bytes.size.toLong() * maxCompression >= f.widthPx.toLong() * f.heightPx * 3

    private fun stepFor(minWidth: Int, maxWidth: Int): Int = maxOf(1, (maxWidth - minWidth) / 4)

    private fun bestUnderCap(width: Int, height: Int, maxBytes: Int?, encoder: JpegEncoder, topQuality: Int): SizeOutcome.Fits? {
        val top = encoder.encode(width, height, topQuality)
        if (maxBytes == null || top.size <= maxBytes) return SizeOutcome.Fits(width, height, topQuality, top)
        var lo = MIN_QUALITY
        var hi = topQuality - 1
        var best: SizeOutcome.Fits? = null
        while (lo <= hi) {
            val q = (lo + hi) / 2
            val bytes = encoder.encode(width, height, q)
            if (bytes.size <= maxBytes) {
                best = SizeOutcome.Fits(width, height, q, bytes)
                lo = q + 1
            } else {
                hi = q - 1
            }
        }
        return best
    }
}
