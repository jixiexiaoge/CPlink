package com.example.feedback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {
    
    /**
     * 压缩并调整图片大小
     * @param inputFile 输入文件
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @param quality 压缩质量 (1-100)
     * @param maxSizeKB 最大文件大小(KB)
     * @return 压缩后的文件
     */
    fun compressAndResizeImage(
        inputFile: File,
        maxWidth: Int = 800,
        maxHeight: Int = 600,
        quality: Int = 80,
        maxSizeKB: Int = 500
    ): File {
        try {
            // 读取原始图片
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: throw Exception("无法读取图片文件")
            
            // 获取图片方向信息
            val orientation = getImageOrientation(inputFile)
            
            // 根据方向调整图片
            val rotatedBitmap = if (orientation != 0) {
                rotateBitmap(bitmap, orientation)
            } else {
                bitmap
            }
            
            // 计算缩放比例
            val scale = calculateScale(rotatedBitmap.width, rotatedBitmap.height, maxWidth, maxHeight)
            
            // 创建缩放后的bitmap
            val scaledBitmap = if (scale < 1) {
                Bitmap.createScaledBitmap(rotatedBitmap, 
                    (rotatedBitmap.width * scale).toInt(),
                    (rotatedBitmap.height * scale).toInt(),
                    true)
            } else {
                rotatedBitmap
            }
            
            // 压缩到指定文件大小
            val outputFile = File(inputFile.parent, "compressed_${inputFile.name}")
            compressToFileSize(scaledBitmap, outputFile, quality, maxSizeKB)
            
            // 释放bitmap内存
            bitmap.recycle()
            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle()
            }
            if (scaledBitmap != rotatedBitmap) {
                scaledBitmap.recycle()
            }
            
            return outputFile
            
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果压缩失败，返回原文件
            return inputFile
        }
    }
    
    /**
     * 获取图片方向信息
     */
    private fun getImageOrientation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: IOException) {
            0
        }
    }
    
    /**
     * 旋转图片
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * 计算缩放比例
     */
    private fun calculateScale(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Float {
        val widthScale = maxWidth.toFloat() / width
        val heightScale = maxHeight.toFloat() / height
        return minOf(widthScale, heightScale, 1.0f)
    }
    
    /**
     * 压缩到指定文件大小
     */
    private fun compressToFileSize(bitmap: Bitmap, outputFile: File, quality: Int, maxSizeKB: Int) {
        var currentQuality = quality
        var outputStream: FileOutputStream
        
        do {
            outputStream = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            outputStream.close()
            
            val fileSizeKB = outputFile.length() / 1024
            
            if (fileSizeKB > maxSizeKB && currentQuality > 10) {
                currentQuality -= 10
            } else {
                break
            }
        } while (fileSizeKB > maxSizeKB && currentQuality > 10)
    }
    
    /**
     * 检查图片文件是否有效
     */
    fun isValidImageFile(file: File): Boolean {
        return try {
            file.exists() && 
            file.length() > 0 && 
            file.length() < 16 * 1024 * 1024 && // 小于16MB
            BitmapFactory.decodeFile(file.absolutePath) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取图片文件大小描述
     */
    fun getFileSizeDescription(file: File): String {
        val sizeBytes = file.length()
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "${sizeBytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * 清理临时文件
     */
    fun cleanupTempFiles(directory: File) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.name.startsWith("compressed_") || 
                    file.name.startsWith("temp_image_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
