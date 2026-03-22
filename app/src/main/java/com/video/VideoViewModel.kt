package com.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class VideoViewModel(private val context: Context) : ViewModel() {
    
    var videoList = mutableStateListOf<VideoModel>()
        private set

    init {
        loadVideosFromDisk()
    }

    private fun loadVideosFromDisk() {
        val prefs = context.getSharedPreferences("video_db_pro", Context.MODE_PRIVATE)
        val savedData = prefs.getStringSet("v_list", emptySet()) ?: emptySet()
        savedData.forEach { data ->
            val parts = data.split("|")
            if (parts.size >= 2) {
                val uri = Uri.parse(parts[0])
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) { }
                if (videoList.none { it.uri == uri }) {
                    videoList.add(VideoModel(uri, parts[1]))
                }
            }
        }
    }

    fun addVideo(uri: Uri, name: String) {
        if (videoList.none { it.uri == uri }) {
            videoList.add(VideoModel(uri, name))
            saveToDisk()
        }
    }

    fun deleteVideo(video: VideoModel) {
        videoList.remove(video)
        saveToDisk()
    }

    fun renameVideo(video: VideoModel, newName: String) {
        val index = videoList.indexOf(video)
        if (index != -1) {
            videoList[index] = video.copy(name = newName)
            saveToDisk()
        }
    }

    private fun saveToDisk() {
        val prefs = context.getSharedPreferences("video_db_pro", Context.MODE_PRIVATE)
        val dataSet = videoList.map { "${it.uri}|${it.name}" }.toSet()
        prefs.edit().putStringSet("v_list", dataSet).apply()
    }
}