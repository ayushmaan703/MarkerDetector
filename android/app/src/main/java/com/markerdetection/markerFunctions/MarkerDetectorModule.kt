package com.markerdetection.markerFunctions

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments

import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

import android.graphics.BitmapFactory
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.Core

class MarkerDetectorModule(
    reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "MarkerDetector"


    @ReactMethod
    fun sayHello(name: String, promise: Promise) {
        promise.resolve("Hello $name from native!")
    }

    @ReactMethod
    fun testOpenCV(promise: Promise) {
        try {
            val isLoaded = OpenCVLoader.initDebug()
            if (!isLoaded) { promise.reject("OPENCV_ERROR", "OpenCV failed to load"); return }
            val mat = Mat()
            promise.resolve("OpenCV working: Mat created successfully")
        } catch (e: Exception) {
            promise.reject("ERROR", e)
        }
    }

    @ReactMethod
    fun processImageToGray(imagePath: String, promise: Promise) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
                ?: run { promise.reject("ERROR", "Invalid image path"); return }
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            val resultBitmap = android.graphics.Bitmap.createBitmap(
                gray.cols(), gray.rows(), android.graphics.Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(gray, resultBitmap)
            val file = java.io.File(
                reactApplicationContext.cacheDir, "gray_${System.currentTimeMillis()}.png"
            )
            java.io.FileOutputStream(file).use {
                resultBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            promise.resolve(file.absolutePath)
        } catch (e: Exception) {
            promise.reject("ERROR", e)
        }
    }


    private fun orderPoints(pts: Array<Point>): Array<Point> {
        val sorted      = pts.sortedBy { it.x + it.y }
        val topLeft     = sorted[0]
        val bottomRight = sorted[3]
        val remaining   = listOf(sorted[1], sorted[2])
        val topRight    = if (remaining[0].x > remaining[1].x) remaining[0] else remaining[1]
        val bottomLeft  = if (remaining[0].x < remaining[1].x) remaining[0] else remaining[1]
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun rotate(mat: Mat, angle: Int): Mat {
        val rotated = Mat()
        when (angle) {
            90   -> Core.rotate(mat, rotated, Core.ROTATE_90_CLOCKWISE)
            180  -> Core.rotate(mat, rotated, Core.ROTATE_180)
            270  -> Core.rotate(mat, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> mat.copyTo(rotated)
        }
        return rotated
    }


    private fun darkScore(mat: Mat): Double {
        var dark = 0
        val total = mat.rows() * mat.cols()
        for (i in 0 until mat.rows())
            for (j in 0 until mat.cols())
                if (mat.get(i, j)[0] < 100) dark++
        return dark.toDouble() / total
    }

    private fun correctOrientationMarker1(mat: Mat): Mat {
        val size       = mat.cols()
        val cornerSize = 60
        val gray       = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        val tlScore = darkScore(gray.submat(0, cornerSize, 0, cornerSize))
        val trScore = darkScore(gray.submat(0, cornerSize, size - cornerSize, size))
        val blScore = darkScore(gray.submat(size - cornerSize, size, 0, cornerSize))
        val brScore = darkScore(gray.submat(size - cornerSize, size, size - cornerSize, size))

        val max = maxOf(tlScore, trScore, blScore, brScore)
        return when (max) {
            tlScore -> mat
            trScore -> rotate(mat, 90)
            brScore -> rotate(mat, 180)
            blScore -> rotate(mat, 270)
            else    -> mat
        }
    }

    private fun correctOrientationMarker2(
        mat: Mat,
        topSolid: Boolean, bottomSolid: Boolean,
        leftSolid: Boolean, rightSolid: Boolean
    ): Mat = when {
        bottomSolid && leftSolid  -> mat
        topSolid    && leftSolid  -> rotate(mat, 90)
        topSolid    && rightSolid -> rotate(mat, 180)
        bottomSolid && rightSolid -> rotate(mat, 270)
        else                      -> mat
    }



    private fun hasInnerSquare(mat: Mat): Boolean {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        val contours  = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binary, contours, hierarchy,
            Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE
        )
        for (contour in contours) {
            val c2f    = MatOfPoint2f(*contour.toArray())
            val peri   = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            if (approx.total() == 4L && Imgproc.contourArea(approx) in 800.0..4000.0) return true
        }
        return false
    }


    data class EdgeStats(
        val meanIntensity: Double, 
        val colVariance: Double,    
        val solidScore: Double      
    )

    private fun edgeStats(strip: Mat): EdgeStats {
        val gray = Mat()
        Imgproc.cvtColor(strip, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)

        val cols = gray.cols()
        val rows = gray.rows()

        val colMeans = DoubleArray(cols) { col ->
            var sum = 0.0
            for (row in 0 until rows) sum += gray.get(row, col)[0]
            sum / rows
        }

        val overallMean = colMeans.average()

        val variance = colMeans.map { (it - overallMean) * (it - overallMean) }.average()

        val solidScore = overallMean * 0.7 + sqrt(variance) * 0.3

        return EdgeStats(overallMean, variance, solidScore)
    }

    private val SPREAD_THRESHOLD = 15.0

    private fun detectAndCorrect(warped: Mat): Mat? {
        val w = warped.cols()
        val h = warped.rows()
        val t = 20

        val topStats    = edgeStats(warped.submat(0,     t,   0, w))
        val bottomStats = edgeStats(warped.submat(h - t, h,   0, w))
        val leftStats   = edgeStats(warped.submat(0,     h,   0, t))
        val rightStats  = edgeStats(warped.submat(0,     h, w - t, w))

        Log.d("Marker", String.format(
            "SCORES → TOP=%.1f(μ=%.1f,σ²=%.1f) BOT=%.1f(μ=%.1f,σ²=%.1f) " +
            "LFT=%.1f(μ=%.1f,σ²=%.1f) RGT=%.1f(μ=%.1f,σ²=%.1f)",
            topStats.solidScore,    topStats.meanIntensity,    topStats.colVariance,
            bottomStats.solidScore, bottomStats.meanIntensity, bottomStats.colVariance,
            leftStats.solidScore,   leftStats.meanIntensity,   leftStats.colVariance,
            rightStats.solidScore,  rightStats.meanIntensity,  rightStats.colVariance
        ))

        val edges = listOf(
            Pair("TOP",    topStats),
            Pair("BOT",    bottomStats),
            Pair("LFT",    leftStats),
            Pair("RGT",    rightStats)
        ).sortedBy { it.second.solidScore }

        val solidGroup = edges.take(2)       // 2 most solid edges
        val dottedGroup = edges.drop(2)      // 2 least solid edges

        val solidMean  = solidGroup.map  { it.second.solidScore }.average()
        val dottedMean = dottedGroup.map { it.second.solidScore }.average()
        val spread     = dottedMean - solidMean

        Log.d("Marker", String.format(
            "solidGroupMean=%.1f dottedGroupMean=%.1f spread=%.1f (threshold=%.1f)",
            solidMean, dottedMean, spread, SPREAD_THRESHOLD
        ))

        if (spread >= SPREAD_THRESHOLD) {
            val solidNames  = solidGroup.map  { it.first }.toSet()
            val topSolid    = "TOP" in solidNames
            val bottomSolid = "BOT" in solidNames
            val leftSolid   = "LFT" in solidNames
            val rightSolid  = "RGT" in solidNames

            val isLShape =
                (topSolid && leftSolid)    ||
                (topSolid && rightSolid)   ||
                (bottomSolid && leftSolid) ||
                (bottomSolid && rightSolid)

            Log.d("Marker", "CASE2 → solidEdges=$solidNames isLShape=$isLShape")

            if (!isLShape) return null

            return correctOrientationMarker2(warped, topSolid, bottomSolid, leftSolid, rightSolid)

        } else {
            val corrected = correctOrientationMarker1(warped)
            val ok        = hasInnerSquare(corrected)
            Log.d("Marker", "CASE1 → spread=%.1f < threshold, hasInnerSquare=$ok".format(spread))
            return if (ok) corrected else null
        }
    }

    @ReactMethod
    fun detectEdgesAndContours(imagePath: String, promise: Promise) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
                ?: run { promise.reject("ERROR", "Invalid image"); return }

            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            val edges = Mat()
            Imgproc.Canny(gray, edges, 70.0, 120.0)

            val contours  = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edges, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
            )

            var maxArea    = 0.0
            var bestSquare: MatOfPoint? = null

            for (contour in contours) {
                val c2f    = MatOfPoint2f(*contour.toArray())
                val peri   = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                if (approx.total() == 4L) {
                    val area = Imgproc.contourArea(approx)
                    if (area >= 2000 && area > maxArea) {
                        maxArea    = area
                        bestSquare = MatOfPoint(*approx.toArray())
                    }
                }
            }

            if (bestSquare == null) {
                promise.reject("ERROR", "No square detected")
                return
            }

            val rect = Imgproc.boundingRect(bestSquare)

            val ordered = orderPoints(bestSquare.toArray())
            val srcMat  = MatOfPoint2f(*ordered)
            val dstMat  = MatOfPoint2f(
                Point(0.0, 0.0), Point(300.0, 0.0),
                Point(300.0, 300.0), Point(0.0, 300.0)
            )
            val M = Imgproc.getPerspectiveTransform(srcMat, dstMat)
            val warped = Mat()
            Imgproc.warpPerspective(src, warped, M, Size(300.0, 300.0))

            val corrected = detectAndCorrect(warped)
                ?: run { promise.reject("ERROR", "Not a valid marker"); return }

            val resultBitmap = Bitmap.createBitmap(
                corrected.cols(), corrected.rows(), Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(corrected, resultBitmap)

            val file = File(reactApplicationContext.cacheDir, "marker_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use {
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            val result = Arguments.createMap()
            result.putInt("x", rect.x)
            result.putInt("y", rect.y)
            result.putInt("width", rect.width)
            result.putInt("height", rect.height)
            result.putString("image", file.absolutePath)
            promise.resolve(result)

        } catch (e: Exception) {
            promise.reject("ERROR", e)
        }
    }
}