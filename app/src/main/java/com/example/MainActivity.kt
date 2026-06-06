package com.example

import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          VideoCoverScreen(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCoverScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var outputVideoFile by remember { mutableStateOf<File?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { 
        videoUri = it
        resultMessage = ""
        outputVideoFile = null
    } }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { 
        imageUri = it 
        resultMessage = ""
        outputVideoFile = null
    } }

    val saveVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri: Uri? ->
        if (uri != null && outputVideoFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(outputVideoFile!!).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        resultMessage = "Saved successfully!"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        resultMessage = "Error saving: ${e.message}"
                    }
                }
            }
        }
    }

    fun processVideo() {
        if (videoUri == null || imageUri == null) {
            resultMessage = "Please select both a video and an image."
            return
        }

        isProcessing = true
        resultMessage = "Processing... this might take a minute."

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Copy video to cache
                val inputVideo = File(context.cacheDir, "input.mp4")
                context.contentResolver.openInputStream(videoUri!!)?.use { input ->
                    FileOutputStream(inputVideo).use { output ->
                        input.copyTo(output)
                    }
                }

                // Read image
                val imageBytes = context.contentResolver.openInputStream(imageUri!!)?.use { input ->
                    input.readBytes()
                } ?: throw Exception("Could not read image bytes")

                // Check extension
                val mimeType = context.contentResolver.getType(imageUri!!)
                val isJpg = mimeType?.contains("jpeg", ignoreCase = true) == true || mimeType?.contains("jpg", ignoreCase = true) == true

                val outVideo = File(context.cacheDir, "output.mp4")

                VideoProcessor.setCover(inputVideo.absolutePath, imageBytes, isJpg, outVideo.absolutePath)

                outputVideoFile = outVideo

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    resultMessage = "Cover embedded! Ready to save."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    resultMessage = "Error: ${e.message}"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Embed Cover to Video", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (videoUri != null) "Video Selected" else "1. Choose Video")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (imageUri != null) "Image Selected" else "2. Choose Cover Image")
        }
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { processVideo() },
            enabled = videoUri != null && imageUri != null && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Processing..." else "3. Embed Cover & Create Video")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (resultMessage.isNotEmpty()) {
            Text(text = resultMessage, color = if (resultMessage.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (outputVideoFile != null && !isProcessing) {
            Button(
                onClick = { saveVideoLauncher.launch("Covered_Video.mp4") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("Save Custom Video")
            }
        }
    }
}
