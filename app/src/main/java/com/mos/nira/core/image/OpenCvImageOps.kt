package com.mos.nira.core.image

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OpenCV implementation of [ImageOps]. This is the only file in the app that touches OpenCV.
 *
 * Used only for: perspective warp, edge-preserving smoothing, morphology, connected components
 * and distance transform. No OpenCV machine-learning or DNN module is used.
 */
class OpenCvImageOps private constructor() : ImageOps {

    override val name: String = "OpenCV"

    override fun perspectiveWarp(
        pixels: IntArray,
        width: Int,
        height: Int,
        corners: FloatArray,
        outWidth: Int,
        outHeight: Int
    ): IntArray {
        require(corners.size == 8) { "corners must hold 4 x,y pairs" }
        if (outWidth <= 0 || outHeight <= 0) return IntArray(0)

        val src = argbToMat(pixels, width, height)
        val dst = Mat()
        val from = MatOfPoint2f(
            Point(corners[0].toDouble(), corners[1].toDouble()),
            Point(corners[2].toDouble(), corners[3].toDouble()),
            Point(corners[4].toDouble(), corners[5].toDouble()),
            Point(corners[6].toDouble(), corners[7].toDouble())
        )
        val to = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((outWidth - 1).toDouble(), 0.0),
            Point((outWidth - 1).toDouble(), (outHeight - 1).toDouble()),
            Point(0.0, (outHeight - 1).toDouble())
        )
        val transform = Imgproc.getPerspectiveTransform(from, to)
        try {
            Imgproc.warpPerspective(
                src, dst, transform, Size(outWidth.toDouble(), outHeight.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE
            )
            return matToArgb(dst, outWidth, outHeight)
        } finally {
            src.release(); dst.release(); from.release(); to.release(); transform.release()
        }
    }

    override fun smoothEdgePreserving(
        pixels: IntArray,
        width: Int,
        height: Int,
        diameterPx: Int,
        sigmaColor: Double,
        sigmaSpace: Double
    ): IntArray {
        if (diameterPx / 2 <= 0 || sigmaColor <= 0.0 || sigmaSpace <= 0.0) return pixels.copyOf()

        // bilateralFilter needs 3 channels, so alpha is carried over from the input.
        val rgb = ByteArray(width * height * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgb[i * 3] = (p shr 16).toByte()
            rgb[i * 3 + 1] = (p shr 8).toByte()
            rgb[i * 3 + 2] = p.toByte()
        }
        val src = Mat(height, width, CvType.CV_8UC3)
        val dst = Mat()
        try {
            src.put(0, 0, rgb)
            Imgproc.bilateralFilter(src, dst, diameterPx, sigmaColor, sigmaSpace)
            dst.get(0, 0, rgb)
        } finally {
            src.release(); dst.release()
        }
        return IntArray(pixels.size) { i ->
            (pixels[i] and 0xFF000000.toInt()) or
                ((rgb[i * 3].toInt() and 0xFF) shl 16) or
                ((rgb[i * 3 + 1].toInt() and 0xFF) shl 8) or
                (rgb[i * 3 + 2].toInt() and 0xFF)
        }
    }

    override fun erode(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        morph(mask, width, height, radiusPx, Imgproc.MORPH_ERODE)

    override fun dilate(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        morph(mask, width, height, radiusPx, Imgproc.MORPH_DILATE)

    override fun open(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        morph(mask, width, height, radiusPx, Imgproc.MORPH_OPEN)

    override fun close(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        morph(mask, width, height, radiusPx, Imgproc.MORPH_CLOSE)

    private fun morph(mask: BooleanArray, width: Int, height: Int, radiusPx: Int, op: Int): BooleanArray {
        if (radiusPx <= 0) return mask.copyOf()
        val side = (2 * radiusPx + 1).toDouble()
        val src = maskToMat(mask, width, height)
        val dst = Mat()
        val element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(side, side))
        try {
            Imgproc.morphologyEx(src, dst, op, element)
            return matToMask(dst, width, height)
        } finally {
            src.release(); dst.release(); element.release()
        }
    }

    override fun connectedComponents(mask: BooleanArray, width: Int, height: Int): ComponentLabels {
        val src = maskToMat(mask, width, height)
        val labelMat = Mat()
        try {
            val total = Imgproc.connectedComponents(src, labelMat, 8, CvType.CV_32S)
            val labels = IntArray(width * height)
            labelMat.get(0, 0, labels)
            val areas = IntArray(total)
            for (label in labels) areas[label]++
            return ComponentLabels(labels, total - 1, areas)
        } finally {
            src.release(); labelMat.release()
        }
    }

    override fun distanceTransform(mask: BooleanArray, width: Int, height: Int): FloatArray {
        val src = maskToMat(mask, width, height)
        val dst = Mat()
        try {
            Imgproc.distanceTransform(src, dst, Imgproc.DIST_L2, Imgproc.DIST_MASK_PRECISE)
            val out = FloatArray(width * height)
            dst.get(0, 0, out)
            return out
        } finally {
            src.release(); dst.release()
        }
    }

    private fun maskToMat(mask: BooleanArray, width: Int, height: Int): Mat {
        require(mask.size == width * height) { "mask does not match width x height" }
        val bytes = ByteArray(mask.size) { if (mask[it]) 255.toByte() else 0 }
        val mat = Mat(height, width, CvType.CV_8UC1)
        mat.put(0, 0, bytes)
        return mat
    }

    private fun matToMask(mat: Mat, width: Int, height: Int): BooleanArray {
        val bytes = ByteArray(width * height)
        mat.get(0, 0, bytes)
        return BooleanArray(bytes.size) { bytes[it].toInt() != 0 }
    }

    private fun argbToMat(pixels: IntArray, width: Int, height: Int): Mat {
        require(pixels.size == width * height) { "pixel buffer does not match width x height" }
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 4] = (p shr 16).toByte()
            bytes[i * 4 + 1] = (p shr 8).toByte()
            bytes[i * 4 + 2] = p.toByte()
            bytes[i * 4 + 3] = (p ushr 24).toByte()
        }
        val mat = Mat(height, width, CvType.CV_8UC4)
        mat.put(0, 0, bytes)
        return mat
    }

    private fun matToArgb(mat: Mat, width: Int, height: Int): IntArray {
        val bytes = ByteArray(width * height * 4)
        mat.get(0, 0, bytes)
        return IntArray(width * height) { i ->
            ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24) or
                ((bytes[i * 4].toInt() and 0xFF) shl 16) or
                ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                (bytes[i * 4 + 2].toInt() and 0xFF)
        }
    }

    companion object {
        @Volatile
        private var instance: ImageOps? = null

        /**
         * Returns the OpenCV implementation, or [KotlinImageOps] if the native library
         * cannot be loaded on this device. Check [ImageOps.name] to see which one is in use.
         */
        fun getOrFallback(): ImageOps {
            instance?.let { return it }
            val loaded = try {
                OpenCVLoader.initLocal()
            } catch (t: Throwable) {
                false
            }
            val ops: ImageOps = if (loaded) OpenCvImageOps() else KotlinImageOps
            instance = ops
            return ops
        }
    }
}
