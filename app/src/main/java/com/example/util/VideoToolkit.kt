package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lossless video tools used by the Video Editor screen.
 *
 * Nothing here decodes or re-encodes a single frame. The compressed video and audio samples are copied
 * 1:1 from the source into a new MP4 (MediaExtractor -> MediaMuxer), so:
 *  - the resolution, frame rate, bitrate and codec of the original are kept exactly,
 *  - the audio stream is copied untouched (no quality loss at all),
 *  - cutting / joining is very fast and does not heat the phone.
 *
 * Because the video is not re-encoded, a cut can only start on a key frame. Split parts therefore never
 * run longer than the length that was asked for (they end on the last key frame before the limit), and
 * the parts follow each other with no frame lost and no frame repeated.
 */
object VideoToolkit {

    /** Folder (inside Movies) where every result is saved automatically. */
    const val OUTPUT_FOLDER = "SkY Touch"

    /** Folder (inside Pictures) where exported frames (PNG, lossless) are saved. */
    const val FRAMES_FOLDER = "SkY Touch Frames"

    /** Expected, user-readable failure (shown as is in the UI). */
    class EditException(message: String) : Exception(message)

    data class VideoInfo(
        val uri: Uri,
        val name: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val sizeBytes: Long
    )

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    fun readInfo(context: Context, uri: Uri): VideoInfo {
        var name = "video"
        var size = -1L
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w; w = h; h = t
            }
            return VideoInfo(uri, name, duration, w, h, size)
        } catch (e: Exception) {
            throw EditException("This file could not be read as a video.")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Cuts one video into consecutive parts that are each at most [segmentSeconds] long and saves every
     * part to Movies/[OUTPUT_FOLDER]. Returns the names of the saved files.
     */
    suspend fun splitVideo(
        context: Context,
        uri: Uri,
        baseName: String,
        segmentSeconds: Int,
        onProgress: (fraction: Float, message: String) -> Unit
    ): List<String> {
        if (segmentSeconds < 1) throw EditException("Part length must be at least 1 second.")
        val src = openSource(context, uri, "the video")
        try {
            val scan = scan(src)
            val base = scan.baseUs
            val starts = computeStarts(scan, segmentSeconds * 1_000_000L)
            val total = (scan.endVideoUs - base).coerceAtLeast(1L)
            val webm = isWebmCodec(src)
            val buffer = ByteBuffer.allocate(bufferSize(src))
            val saved = ArrayList<String>()
            val safeBase = sanitizeName(baseName)

            for (i in starts.indices) {
                currentCoroutineContext().ensureActive()
                val startUs = starts[i]
                val nextStartUs = if (i + 1 < starts.size) starts[i + 1] else Long.MAX_VALUE
                val label = "Part ${i + 1} of ${starts.size}"
                onProgress((startUs - base).toFloat() / total, "Saving $label")

                val out = OutFile(context, "${safeBase}_part${(i + 1).toString().padStart(3, '0')}", webm)
                var ok = false
                var muxer: MediaMuxer? = null
                try {
                    muxer = out.newMuxer()
                    muxer.setOrientationHint(src.rotation)
                    val dstV = muxer.addTrack(src.videoFormat)
                    val dstA = src.audioFormat?.let { muxer.addTrack(it) } ?: -1
                    muxer.start()
                    copyRange(
                        src = src, muxer = muxer, dstVideo = dstV, dstAudio = dstA,
                        startUs = startUs,
                        videoEndKeyUs = nextStartUs,
                        audioEndUs = nextStartUs,
                        outOffsetUs = 0L,
                        buffer = buffer
                    ) { videoTimeUs ->
                        onProgress(((videoTimeUs - base).toFloat() / total).coerceIn(0f, 1f), "Saving $label")
                    }
                    muxer.stop()
                    ok = true
                } catch (e: EditException) {
                    throw e
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw EditException("Could not write part ${i + 1}: ${e.message ?: "unsupported video format"}")
                } finally {
                    try {
                        muxer?.release()
                    } catch (_: Exception) {
                    }
                    if (ok) out.commit() else out.discard()
                }
                saved.add(out.fileName)
            }
            onProgress(1f, "Done")
            return saved
        } finally {
            src.release()
        }
    }

    /**
     * Joins the videos (in the given order) into one file with no gap between them and saves it to
     * Movies/[OUTPUT_FOLDER]. Video and audio are copied untouched, so the original resolution and audio
     * quality are kept. All clips must share the same codec settings (normal for clips from one camera).
     */
    suspend fun mergeVideos(
        context: Context,
        uris: List<Uri>,
        forceCombine: Boolean = false,
        onProgress: (fraction: Float, message: String) -> Unit
    ): String {
        if (uris.size < 2) throw EditException("Add at least two videos to combine.")
        val sources = ArrayList<Source>()
        try {
            uris.forEachIndexed { i, u ->
                onProgress(0f, "Reading clip ${i + 1} of ${uris.size}")
                sources.add(openSource(context, u, "clip ${i + 1}"))
            }
            checkCompatible(sources, forceCombine)

            val scans = ArrayList<Scan>()
            sources.forEachIndexed { i, s ->
                onProgress(0f, "Analysing clip ${i + 1} of ${sources.size}")
                scans.add(scan(s))
            }
            // Each clip occupies exactly [its first sample, end of its last video frame): the next clip starts
            // on the very next frame, so there is no black frame, freeze or gap at the joins.
            val lengths = scans.map { (it.endVideoUs - it.baseUs).coerceAtLeast(1L) }
            val totalUs = lengths.sum().coerceAtLeast(1L)

            val first = sources[0]
            val webm = isWebmCodec(first)
            val buffer = ByteBuffer.allocate(sources.maxOf { bufferSize(it) })
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = OutFile(context, "Merged_$stamp", webm)
            var ok = false
            var muxer: MediaMuxer? = null
            try {
                muxer = out.newMuxer()
                muxer.setOrientationHint(first.rotation)
                // MediaFormat(other) needs API 29, so the first clip's own format object is updated instead
                first.videoFormat.setLong(MediaFormat.KEY_DURATION, totalUs)
                val dstV = muxer.addTrack(first.videoFormat)
                val audioSource = sources.firstOrNull { it.audioFormat != null }
                val dstA = audioSource?.audioFormat?.let { muxer.addTrack(it) } ?: -1
                muxer.start()

                var offsetUs = 0L
                for (i in sources.indices) {
                    currentCoroutineContext().ensureActive()
                    val s = sources[i]
                    val sc = scans[i]
                    val doneBefore = offsetUs
                    val label = "Joining clip ${i + 1} of ${sources.size}"
                    copyRange(
                        src = s, muxer = muxer, dstVideo = dstV, dstAudio = dstA,
                        startUs = sc.baseUs,
                        videoEndKeyUs = Long.MAX_VALUE,
                        audioEndUs = sc.baseUs + lengths[i],
                        outOffsetUs = offsetUs,
                        buffer = buffer
                    ) { videoTimeUs ->
                        val f = (doneBefore + (videoTimeUs - sc.baseUs)).toFloat() / totalUs
                        onProgress(f.coerceIn(0f, 1f), label)
                    }
                    offsetUs += lengths[i]
                }
                muxer.stop()
                ok = true
            } catch (e: EditException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw EditException("Could not write the combined video: ${e.message ?: "unsupported video format"}")
            } finally {
                try {
                    muxer?.release()
                } catch (_: Exception) {
                }
                if (ok) out.commit() else out.discard()
            }
            onProgress(1f, "Done")
            return out.fileName
        } finally {
            sources.forEach { it.release() }
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Frame editor: analysis, remove, insert, export
    // ---------------------------------------------------------------------------------------------

    /** Key-frame map of a video. All "Ms" values used by the UI are relative to the first frame. */
    class Analysis(
        val keyframesUs: LongArray,
        val baseUs: Long,
        val endUs: Long,
        val fps: Float
    ) {
        val durationMs: Long get() = ((endUs - baseUs) / 1000L).coerceAtLeast(1L)

        /** Last key frame at or before the position (a lossless cut / insert can only happen on a key frame). */
        fun snapBeforeUs(absUs: Long): Long {
            var r = baseUs
            for (k in keyframesUs) {
                if (k <= absUs) r = k else break
            }
            return r
        }

        /** First key frame at or after the position (or the end of the video). */
        fun snapAfterUs(absUs: Long): Long = keyframesUs.firstOrNull { it >= absUs } ?: endUs

        /** The range that is really removed for a selected range: it always covers the selected frames. */
        fun snapRangeMs(startMs: Long, endMs: Long): Pair<Long, Long> {
            val a = snapBeforeUs(baseUs + startMs * 1000L)
            val b = snapAfterUs(baseUs + endMs * 1000L)
            return Pair((a - baseUs) / 1000L, (b - baseUs) / 1000L)
        }

        fun snapInsertMs(posMs: Long): Long = (snapBeforeUs(baseUs + posMs * 1000L) - baseUs) / 1000L
    }

    suspend fun analyze(context: Context, uri: Uri): Analysis {
        val src = openSource(context, uri, "the video")
        try {
            val sc = scan(src)
            val span = (sc.lastVideoUs - sc.firstVideoUs).coerceAtLeast(1L)
            val fps = if (sc.frameCount > 1) (sc.frameCount - 1) * 1_000_000f / span else 30f
            return Analysis(sc.keyframesUs, sc.baseUs, sc.endVideoUs, fps.coerceIn(1f, 240f))
        } finally {
            src.release()
        }
    }

    /** One frame for the editor preview (full decode of that exact frame, scaled down for the screen only). */
    fun previewFrame(context: Context, uri: Uri, timeUs: Long, maxSide: Int = 720): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val b = r.getFrameAtTime(timeUs.coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
            val s = maxSide.toFloat() / maxOf(b.width, b.height)
            if (s >= 1f) return b
            val scaled = Bitmap.createScaledBitmap(
                b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true
            )
            if (scaled !== b) b.recycle()
            return scaled
        } catch (e: Exception) {
            return null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Removes the selected sections ([rangesMs] = start/end in ms from the video start) without re-encoding.
     * Each section is widened to the key frames around it (see [Analysis.snapRangeMs]) so no partial group
     * of pictures is left behind; everything that stays is copied untouched.
     */
    suspend fun removeSections(
        context: Context,
        uri: Uri,
        baseName: String,
        rangesMs: List<Pair<Long, Long>>,
        onProgress: (fraction: Float, message: String) -> Unit
    ): String {
        if (rangesMs.isEmpty()) throw EditException("Select at least one section to remove.")
        val src = openSource(context, uri, "the video")
        try {
            val sc = scan(src)
            val a = Analysis(sc.keyframesUs, sc.baseUs, sc.endVideoUs, 30f)
            val snapped = rangesMs
                .filter { it.second > it.first }
                .map {
                    Pair(a.snapBeforeUs(a.baseUs + it.first * 1000L), a.snapAfterUs(a.baseUs + it.second * 1000L))
                }
                .sortedBy { it.first }
            if (snapped.isEmpty()) throw EditException("Select at least one section to remove.")
            val merged = ArrayList<Pair<Long, Long>>()
            for (r in snapped) {
                val last = merged.lastOrNull()
                if (last != null && r.first <= last.second) {
                    merged[merged.size - 1] = Pair(last.first, maxOf(last.second, r.second))
                } else {
                    merged.add(r)
                }
            }

            val end = sc.endVideoUs
            val pieces = ArrayList<Piece>()
            var cursor = sc.baseUs
            for ((sa, sb) in merged) {
                if (sa > cursor) pieces.add(Piece(src, cursor, sa, sa, sa - cursor))
                cursor = maxOf(cursor, sb)
            }
            if (cursor < end) pieces.add(Piece(src, cursor, Long.MAX_VALUE, end, end - cursor))
            if (pieces.isEmpty()) throw EditException("Nothing would be left of the video.")

            val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
            return runPieces(context, "${sanitizeName(baseName)}_edited_$stamp", pieces, onProgress, "Removing frames")
        } finally {
            src.release()
        }
    }

    /**
     * Inserts another video into [mainUri] at [atMs] (moved back to the key frame at or before that
     * position, so nothing is re-encoded). Both videos must share the same codec settings.
     */
    suspend fun insertClip(
        context: Context,
        mainUri: Uri,
        clipUri: Uri,
        baseName: String,
        atMs: Long,
        forceCombine: Boolean = false,
        onProgress: (fraction: Float, message: String) -> Unit
    ): String {
        val main = openSource(context, mainUri, "the main video")
        var clip: Source? = null
        try {
            clip = openSource(context, clipUri, "the video to insert")
            try {
                checkCompatible(listOf(main, clip), forceCombine)
            } catch (e: EditException) {
                throw EditException((e.message ?: "Videos do not match.").replace("Clip 2", "The video to insert"))
            }
            val sm = scan(main)
            val sc = scan(clip)
            val a = Analysis(sm.keyframesUs, sm.baseUs, sm.endVideoUs, 30f)
            val k = a.snapBeforeUs(a.baseUs + atMs * 1000L)

            val pieces = ArrayList<Piece>()
            if (k > sm.baseUs) pieces.add(Piece(main, sm.baseUs, k, k, k - sm.baseUs))
            pieces.add(Piece(clip, sc.baseUs, Long.MAX_VALUE, sc.endVideoUs, sc.endVideoUs - sc.baseUs))
            if (k < sm.endVideoUs) pieces.add(Piece(main, k, Long.MAX_VALUE, sm.endVideoUs, sm.endVideoUs - k))

            val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
            return runPieces(context, "${sanitizeName(baseName)}_inserted_$stamp", pieces, onProgress, "Inserting video")
        } finally {
            main.release()
            clip?.release()
        }
    }

    /**
     * Saves frames as PNG (lossless) to Pictures/[FRAMES_FOLDER].
     * [stepUs] = 0 exports every frame of the range, otherwise one frame every [stepUs].
     */
    suspend fun exportFrames(
        context: Context,
        uri: Uri,
        baseName: String,
        baseUs: Long,
        fromMs: Long,
        toMs: Long,
        stepUs: Long,
        fps: Float,
        maxFrames: Int,
        onProgress: (fraction: Float, message: String) -> Unit
    ): List<String> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
        } catch (e: Exception) {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            throw EditException("This file could not be read as a video.")
        }
        try {
            val startUs = fromMs * 1000L
            val spanUs = (toMs - fromMs).coerceAtLeast(0L) * 1000L
            val stepD = if (stepUs > 0) stepUs.toDouble() else 1_000_000.0 / fps
            val count = if (spanUs <= 0L) 1 else (spanUs / stepD).toInt() + 1
            if (count > maxFrames) {
                throw EditException("That would export $count frames (maximum $maxFrames at once). Choose a shorter range or a bigger step.")
            }
            val safe = sanitizeName(baseName)
            val saved = ArrayList<String>()
            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                onProgress(i.toFloat() / count, "Exporting frame ${i + 1} of $count")
                val relUs = startUs + (i * stepD).toLong()
                val bmp = retriever.getFrameAtTime(baseUs + relUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: throw EditException("This device could not decode the frame at ${relUs / 1000} ms.")
                try {
                    saved.add(savePng(context, "${safe}_${(relUs / 1000).toString().padStart(8, '0')}ms", bmp))
                } finally {
                    bmp.recycle()
                }
            }
            onProgress(1f, "Done")
            return saved
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun savePng(context: Context, nameNoExt: String, bmp: Bitmap): String {
        val fileName = "$nameNoExt.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FRAMES_FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val u = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw EditException("Could not create the image file in Pictures/$FRAMES_FOLDER.")
            try {
                val stream = context.contentResolver.openOutputStream(u)
                    ?: throw EditException("Could not open the image file for writing.")
                stream.use {
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) throw EditException("Could not write the PNG.")
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(u, done, null, null)
            } catch (e: Exception) {
                try {
                    context.contentResolver.delete(u, null, null)
                } catch (_: Exception) {
                }
                throw e
            }
            return fileName
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FRAMES_FOLDER)
            if (!dir.exists() && !dir.mkdirs()) {
                throw EditException("Could not create the folder Pictures/$FRAMES_FOLDER (storage permission needed).")
            }
            val f = File(dir, fileName)
            FileOutputStream(f).use {
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) throw EditException("Could not write the PNG.")
            }
            MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf("image/png"), null)
            return f.name
        }
    }

    private class Piece(
        val src: Source,
        val startUs: Long,
        val videoEndKeyUs: Long,
        val audioEndUs: Long,
        val lengthUs: Long
    )

    /** Writes the pieces one after another (no gap) into a single new file. */
    private suspend fun runPieces(
        context: Context,
        outName: String,
        pieces: List<Piece>,
        onProgress: (Float, String) -> Unit,
        label: String
    ): String {
        val first = pieces[0].src
        val webm = isWebmCodec(first)
        val buffer = ByteBuffer.allocate(pieces.maxOf { bufferSize(it.src) })
        val totalUs = pieces.sumOf { it.lengthUs }.coerceAtLeast(1L)
        val out = OutFile(context, outName, webm)
        var ok = false
        var muxer: MediaMuxer? = null
        try {
            muxer = out.newMuxer()
            muxer.setOrientationHint(first.rotation)
            first.videoFormat.setLong(MediaFormat.KEY_DURATION, totalUs)
            val dstV = muxer.addTrack(first.videoFormat)
            val dstA = first.audioFormat?.let { muxer.addTrack(it) } ?: -1
            muxer.start()
            var offsetUs = 0L
            for (p in pieces) {
                currentCoroutineContext().ensureActive()
                val doneBefore = offsetUs
                copyRange(
                    src = p.src, muxer = muxer, dstVideo = dstV, dstAudio = dstA,
                    startUs = p.startUs,
                    videoEndKeyUs = p.videoEndKeyUs,
                    audioEndUs = p.audioEndUs,
                    outOffsetUs = offsetUs,
                    buffer = buffer
                ) { t ->
                    val f = (doneBefore + (t - p.startUs)).toFloat() / totalUs
                    onProgress(f.coerceIn(0f, 1f), label)
                }
                offsetUs += p.lengthUs
            }
            muxer.stop()
            ok = true
        } catch (e: EditException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw EditException("Could not write the video: ${e.message ?: "unsupported video format"}")
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            if (ok) out.commit() else out.discard()
        }
        onProgress(1f, "Done")
        return out.fileName
    }

    // ---------------------------------------------------------------------------------------------
    // Source handling
    // ---------------------------------------------------------------------------------------------

    private class Source(
        val extractor: MediaExtractor,
        val videoIdx: Int,
        val audioIdx: Int,
        val videoFormat: MediaFormat,
        val audioFormat: MediaFormat?,
        val rotation: Int
    ) {
        fun release() {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun openSource(context: Context, uri: Uri, what: String): Source {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
        } catch (e: Exception) {
            ex.release()
            throw EditException("Could not open $what.")
        }
        var v = -1
        var a = -1
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (v < 0 && mime.startsWith("video/")) v = i
            else if (a < 0 && mime.startsWith("audio/")) a = i
        }
        if (v < 0) {
            ex.release()
            throw EditException("$what has no video track.")
        }

        var rotation = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        return Source(ex, v, a, ex.getTrackFormat(v), if (a >= 0) ex.getTrackFormat(a) else null, rotation)
    }

    private fun isWebmCodec(src: Source): Boolean {
        val m = src.videoFormat.getString(MediaFormat.KEY_MIME) ?: return false
        return m == MediaFormat.MIMETYPE_VIDEO_VP8 || m == MediaFormat.MIMETYPE_VIDEO_VP9
    }

    private fun bufferSize(src: Source): Int {
        var m = 0
        for (f in listOfNotNull(src.videoFormat, src.audioFormat)) {
            if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) m = maxOf(m, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        }
        return maxOf(m, 8 * 1024 * 1024)
    }

    // ---------------------------------------------------------------------------------------------
    // Analysis (metadata only: no sample data is read)
    // ---------------------------------------------------------------------------------------------

    private class Scan(
        val keyframesUs: LongArray,
        /** Time of the very first sample (video or audio). Everything is shifted so this becomes 0. */
        val baseUs: Long,
        /** End of the last video frame. */
        val endVideoUs: Long,
        val frameCount: Int,
        val firstVideoUs: Long,
        val lastVideoUs: Long
    )

    private suspend fun scan(src: Source): Scan {
        val ex = src.extractor
        ex.selectTrack(src.videoIdx)
        val keys = ArrayList<Long>()
        var firstVideo = -1L
        var maxT = 0L
        var n = 0
        try {
            ex.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (true) {
                val t = ex.sampleTime
                if (t < 0) break
                if (firstVideo < 0) firstVideo = t
                if (t > maxT) maxT = t
                if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) keys.add(t)
                n++
                if (n % 512 == 0) currentCoroutineContext().ensureActive()
                if (!ex.advance()) break
            }
        } finally {
            ex.unselectTrack(src.videoIdx)
        }
        if (firstVideo < 0 || n == 0) throw EditException("The video track is empty or damaged.")
        if (keys.isEmpty()) keys.add(firstVideo)
        keys.sort()

        val avgFrame = if (n > 1) (maxT - firstVideo) / (n - 1) else 33_333L
        val end = maxT + avgFrame

        var base = firstVideo
        if (src.audioIdx >= 0) {
            ex.selectTrack(src.audioIdx)
            try {
                ex.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val ta = ex.sampleTime
                if (ta in 0 until base) base = ta
            } finally {
                ex.unselectTrack(src.audioIdx)
            }
        }
        return Scan(keys.toLongArray(), base, end, n, firstVideo, maxT)
    }

    /**
     * Start time of each part. A part ends on the last key frame that keeps it within [segUs]; if a single
     * key-frame interval is longer than the requested length the next key frame is used instead (a
     * lossless cut cannot start anywhere else).
     */
    private fun computeStarts(scan: Scan, segUs: Long): List<Long> {
        val keys = scan.keyframesUs
        val starts = arrayListOf(scan.baseUs)
        var prev = scan.baseUs
        while (true) {
            val nominal = prev + segUs
            if (nominal >= scan.endVideoUs) break
            var pick = -1L
            for (k in keys) {
                if (k > prev && k <= nominal) pick = k
                if (k > nominal) break
            }
            if (pick < 0) {
                pick = keys.firstOrNull { it > nominal } ?: break
            }
            starts.add(pick)
            prev = pick
        }
        return starts
    }

    // ---------------------------------------------------------------------------------------------
    // Compatibility check for joining
    // ---------------------------------------------------------------------------------------------

    private fun checkCompatible(sources: List<Source>, forceCombine: Boolean = false) {
        val a = sources[0]
        for (i in 1 until sources.size) {
            val b = sources[i]
            val n = i + 1
            fun fail(reason: String): Nothing = throw EditException(
                "Clip $n can't be joined without re-encoding: $reason. " +
                    "Lossless joining needs clips with identical video/audio settings (for example clips recorded with the same camera settings). " +
                    "You can turn on 'Force combine clips' to bypass this check."
            )

            val va = a.videoFormat
            val vb = b.videoFormat
            if (!forceCombine && va.getString(MediaFormat.KEY_MIME) != vb.getString(MediaFormat.KEY_MIME)) {
                fail("different video codec")
            }
            if (!forceCombine && (
                va.getInteger(MediaFormat.KEY_WIDTH) != vb.getInteger(MediaFormat.KEY_WIDTH) ||
                va.getInteger(MediaFormat.KEY_HEIGHT) != vb.getInteger(MediaFormat.KEY_HEIGHT)
            )) {
                fail(
                    "different resolution (${va.getInteger(MediaFormat.KEY_WIDTH)}x${va.getInteger(MediaFormat.KEY_HEIGHT)} " +
                        "vs ${vb.getInteger(MediaFormat.KEY_WIDTH)}x${vb.getInteger(MediaFormat.KEY_HEIGHT)})"
                )
            }
            if (!forceCombine && a.rotation != b.rotation) fail("different orientation")
            if (!forceCombine && (!sameCsd(va, vb, "csd-0") || !sameCsd(va, vb, "csd-1"))) {
                fail("different video encoder settings")
            }

            val aa = a.audioFormat
            val ab = b.audioFormat
            if (!forceCombine && (aa == null) != (ab == null)) fail("one clip has no audio")
            if (aa != null && ab != null && !forceCombine) {
                if (aa.getString(MediaFormat.KEY_MIME) != ab.getString(MediaFormat.KEY_MIME)) fail("different audio codec")
                if (aa.getInteger(MediaFormat.KEY_SAMPLE_RATE) != ab.getInteger(MediaFormat.KEY_SAMPLE_RATE) ||
                    aa.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != ab.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                ) {
                    fail("different audio format")
                }
                if (!sameCsd(aa, ab, "csd-0")) fail("different audio settings")
            }
        }
    }

    private fun sameCsd(a: MediaFormat, b: MediaFormat, key: String): Boolean {
        val x = if (a.containsKey(key)) a.getByteBuffer(key) else null
        val y = if (b.containsKey(key)) b.getByteBuffer(key) else null
        if (x == null && y == null) return true
        if (x == null || y == null) return false
        return x.duplicate().rewind() == y.duplicate().rewind()
    }

    // ---------------------------------------------------------------------------------------------
    // The actual lossless copy
    // ---------------------------------------------------------------------------------------------

    /**
     * Copies the samples of one source into [muxer].
     *
     * @param startUs first video key frame to copy (audio before this time is skipped)
     * @param videoEndKeyUs video stops at the first key frame at or after this time (Long.MAX_VALUE = to the end)
     * @param audioEndUs audio stops at this time (Long.MAX_VALUE = to the end)
     * @param outOffsetUs added to every timestamp (used when joining clips)
     */
    private suspend fun copyRange(
        src: Source,
        muxer: MediaMuxer,
        dstVideo: Int,
        dstAudio: Int,
        startUs: Long,
        videoEndKeyUs: Long,
        audioEndUs: Long,
        outOffsetUs: Long,
        buffer: ByteBuffer,
        onVideoTime: (Long) -> Unit
    ) {
        val ex = src.extractor
        val hasAudio = src.audioIdx >= 0 && dstAudio >= 0
        ex.selectTrack(src.videoIdx)
        if (hasAudio) ex.selectTrack(src.audioIdx)
        try {
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = !hasAudio
            var count = 0
            var lastReport = 0L

            while (!(videoDone && audioDone)) {
                val ti = ex.sampleTrackIndex
                if (ti < 0) break
                val t = ex.sampleTime
                val flags = ex.sampleFlags
                val sync = (flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0

                if (++count % 64 == 0) currentCoroutineContext().ensureActive()

                if (ti == src.videoIdx) {
                    if (videoDone) {
                        ex.advance(); continue
                    }
                    if (videoEndKeyUs != Long.MAX_VALUE && sync && t >= videoEndKeyUs) {
                        videoDone = true
                        ex.advance(); continue
                    }
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val outUs = (t - startUs).coerceAtLeast(0L) + outOffsetUs
                    info.set(0, size, outUs, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(dstVideo, buffer, info)
                    if (t - lastReport > 250_000L || t < lastReport) {
                        lastReport = t
                        onVideoTime(t)
                    }
                } else if (ti == src.audioIdx && hasAudio) {
                    if (audioDone) {
                        ex.advance(); continue
                    }
                    if (audioEndUs != Long.MAX_VALUE && t >= audioEndUs) {
                        audioDone = true
                        ex.advance(); continue
                    }
                    if (t < startUs) {
                        ex.advance(); continue
                    }
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val outUs = (t - startUs) + outOffsetUs
                    info.set(0, size, outUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    muxer.writeSampleData(dstAudio, buffer, info)
                } else {
                    ex.advance(); continue
                }
                if (!ex.advance()) break
            }
        } finally {
            try {
                ex.unselectTrack(src.videoIdx)
            } catch (_: Exception) {
            }
            if (hasAudio) {
                try {
                    ex.unselectTrack(src.audioIdx)
                } catch (_: Exception) {
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Output (saved automatically to Movies/SkY Touch)
    // ---------------------------------------------------------------------------------------------

    private fun sanitizeName(name: String): String {
        val noExt = name.substringBeforeLast('.', name)
        val clean = noExt.replace(Regex("[^A-Za-z0-9 _\\-()]"), "_").trim().take(60)
        return if (clean.isEmpty()) "video" else clean
    }

    private class OutFile(private val context: Context, nameNoExt: String, private val webm: Boolean) {
        private val ext = if (webm) "webm" else "mp4"
        private val mime = if (webm) "video/webm" else "video/mp4"
        private var uri: Uri? = null
        private var file: File? = null
        private var pfd: ParcelFileDescriptor? = null
        var fileName: String = "$nameNoExt.$ext"
            private set

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$nameNoExt.$ext")
                    put(MediaStore.Video.Media.MIME_TYPE, mime)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$OUTPUT_FOLDER")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val u = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw EditException("Could not create the output file in Movies/$OUTPUT_FOLDER.")
                uri = u
                pfd = context.contentResolver.openFileDescriptor(u, "rw")
                    ?: throw EditException("Could not open the output file for writing.")
                // MediaStore may rename on a name clash; show the real name
                try {
                    context.contentResolver.query(u, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) fileName = c.getString(0) ?: fileName
                    }
                } catch (_: Exception) {
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), OUTPUT_FOLDER)
                if (!dir.exists() && !dir.mkdirs()) {
                    throw EditException("Could not create the folder Movies/$OUTPUT_FOLDER (storage permission needed).")
                }
                var f = File(dir, "$nameNoExt.$ext")
                var n = 1
                while (f.exists()) {
                    f = File(dir, "${nameNoExt}_$n.$ext")
                    n++
                }
                file = f
                fileName = f.name
            }
        }

        fun newMuxer(): MediaMuxer {
            val format = if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            val p = pfd
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && p != null) {
                MediaMuxer(p.fileDescriptor, format)
            } else {
                MediaMuxer(file!!.absolutePath, format)
            }
        }

        /** Call after the muxer was stopped successfully: makes the file visible in the gallery. */
        fun commit() {
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
            pfd = null
            val u = uri
            if (u != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    context.contentResolver.update(u, values, null, null)
                } catch (_: Exception) {
                }
            }
            file?.let {
                MediaScannerConnection.scanFile(context, arrayOf(it.absolutePath), arrayOf(mime), null)
            }
        }

        /** Removes a half-written file (failure or cancel). */
        fun discard() {
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
            pfd = null
            try {
                uri?.let { context.contentResolver.delete(it, null, null) }
            } catch (_: Exception) {
            }
            try {
                file?.delete()
            } catch (_: Exception) {
            }
        }
    }
}
