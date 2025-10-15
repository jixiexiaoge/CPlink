package com.example.feedback

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_feedback.*
import java.io.File

class FeedbackActivity : AppCompatActivity() {
    
    private val apiService = FeedbackApiService()
    private val selectedImages = mutableListOf<File>()
    private var progressDialog: ProgressDialog? = null
    
    companion object {
        private const val REQUEST_CODE_SELECT_IMAGES = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)
        
        setupUI()
    }
    
    private fun setupUI() {
        // 设置提交按钮点击事件
        submitButton.setOnClickListener {
            submitFeedback()
        }
        
        // 设置图片选择按钮
        selectImageButton.setOnClickListener {
            selectImages()
        }
        
        // 设置清除图片按钮
        clearImagesButton.setOnClickListener {
            clearImages()
        }
    }
    
    private fun submitFeedback() {
        if (!validateForm()) {
            return
        }
        
        val userId = userIdEditText.text.toString().trim()
        val feedback = feedbackEditText.text.toString().trim()
        
        // 显示加载状态
        showProgressDialog()
        
        // 压缩图片（可选）
        val compressedImages = selectedImages.map { imageFile ->
            ImageUtils.compressAndResizeImage(imageFile)
        }
        
        apiService.submitFeedback(
            userId = userId,
            feedback = feedback,
            images = compressedImages.takeIf { it.isNotEmpty() }
        ) { success, message ->
            runOnUiThread {
                hideProgressDialog()
                
                if (success) {
                    Toast.makeText(this, "反馈提交成功！", Toast.LENGTH_SHORT).show()
                    clearForm()
                } else {
                    handleError(message)
                }
            }
        }
    }
    
    private fun validateForm(): Boolean {
        val userId = userIdEditText.text.toString().trim()
        val feedback = feedbackEditText.text.toString().trim()
        
        when {
            userId.isEmpty() -> {
                userIdEditText.error = "请输入用户ID"
                userIdEditText.requestFocus()
                return false
            }
            feedback.isEmpty() -> {
                feedbackEditText.error = "请输入反馈内容"
                feedbackEditText.requestFocus()
                return false
            }
            feedback.length < 10 -> {
                feedbackEditText.error = "反馈内容至少10个字符"
                feedbackEditText.requestFocus()
                return false
            }
            selectedImages.size > 2 -> {
                Toast.makeText(this, "最多只能选择2张图片", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }
    
    private fun handleError(message: String) {
        when {
            message.contains("网络错误") -> {
                Toast.makeText(this, "网络连接失败，请检查网络设置", Toast.LENGTH_LONG).show()
            }
            message.contains("超时") -> {
                Toast.makeText(this, "请求超时，请重试", Toast.LENGTH_SHORT).show()
            }
            message.contains("服务器错误") -> {
                Toast.makeText(this, "服务器暂时不可用，请稍后重试", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this, "提交失败: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun selectImages() {
        if (selectedImages.size >= 2) {
            Toast.makeText(this, "最多只能选择2张图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGES)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SELECT_IMAGES && resultCode == RESULT_OK) {
            data?.let { intent ->
                val clipData = intent.clipData
                if (clipData != null) {
                    // 多选图片
                    for (i in 0 until minOf(clipData.itemCount, 2 - selectedImages.size)) {
                        val uri = clipData.getItemAt(i).uri
                        val file = getFileFromUri(uri)
                        file?.let { selectedImages.add(it) }
                    }
                } else {
                    // 单选图片
                    intent.data?.let { uri ->
                        val file = getFileFromUri(uri)
                        file?.let { selectedImages.add(it) }
                    }
                }
                updateImagePreview()
            }
        }
    }
    
    private fun getFileFromUri(uri: android.net.Uri): File? {
        // 这里需要根据你的需求实现URI到File的转换
        // 可以使用FileProvider或者直接使用URI
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            inputStream?.use { it.copyTo(file.outputStream()) }
            file
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateImagePreview() {
        imageCountText.text = "已选择 ${selectedImages.size} 张图片"
        imageCountText.visibility = if (selectedImages.isNotEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        
        clearImagesButton.visibility = if (selectedImages.isNotEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    private fun clearImages() {
        selectedImages.clear()
        updateImagePreview()
        Toast.makeText(this, "已清除所有图片", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearForm() {
        userIdEditText.text.clear()
        feedbackEditText.text.clear()
        clearImages()
    }
    
    private fun showProgressDialog() {
        progressDialog = ProgressDialog(this)
        progressDialog?.setMessage("正在提交反馈...")
        progressDialog?.setCancelable(false)
        progressDialog?.show()
    }
    
    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
}
