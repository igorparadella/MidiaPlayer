package com.example.midiaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.IOException

data class Music(val title: String, val artist: String, val uri: Uri)

class MainActivity : AppCompatActivity() {

    private lateinit var musicListContainer: LinearLayout
    private lateinit var miniPlayer: LinearLayout
    private lateinit var nowPlayingText: TextView
    private lateinit var playPauseButton: TextView
    private lateinit var prevButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var seekBar: SeekBar

    private var mediaPlayer: MediaPlayer? = null
    private var currentMusicUri: Uri? = null
    private var currentPlayingIconView: TextView? = null

    private var musicList: List<Music> = emptyList()
    private var currentIndex: Int = -1
    private var updateSeekBarRunnable: Runnable? = null
    private val handler = Handler()

    private var randomQueue: MutableList<Int> = mutableListOf()
    private var isShuffleMode: Boolean = false

    // Declaração do launcher fora do onCreate
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadMusic()
            } else {
                Toast.makeText(this, "Permissão para notificações negada", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        musicListContainer = findViewById(R.id.musicListContainer)
        miniPlayer = findViewById(R.id.miniPlayer)
        nowPlayingText = findViewById(R.id.nowPlayingText)
        playPauseButton = findViewById(R.id.playPauseButton)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        seekBar = findViewById(R.id.seekBar)

        val playAllButton: Button = findViewById(R.id.playAllButton)
        val shuffleButton: Button = findViewById(R.id.shuffleButton)

        // Botão "Reproduzir" toca todas as músicas na ordem
        playAllButton.setOnClickListener {
            if (musicList.isNotEmpty()) {
                currentIndex = 0
                isShuffleMode = false
                playMusic(currentIndex)
            }
        }

        // Botão "Aleatório"
        shuffleButton.setOnClickListener {
            if (musicList.isNotEmpty()) {
                randomQueue = musicList.indices.shuffled().toMutableList()
                currentIndex = 0
                isShuffleMode = true
                playMusic(randomQueue[currentIndex])
            }
        }

        playPauseButton.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                playPauseButton.text = "▶"
            } else {
                mediaPlayer?.start()
                playPauseButton.text = "⏸︎"
            }
        }

        prevButton.setOnClickListener {
            if (musicList.isNotEmpty()) {
                navigateMusic(-1)
            }
        }

        nextButton.setOnClickListener {
            if (musicList.isNotEmpty()) {
                navigateMusic(1)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        checkPermissionsAndLoadMusic()
    }

    private fun navigateMusic(direction: Int) {
        if (isShuffleMode) {
            currentIndex = (currentIndex + direction).takeIf { it in randomQueue.indices } ?: randomQueue.size - 1
            playMusic(randomQueue[currentIndex])
        } else {
            currentIndex = (currentIndex + direction).takeIf { it in musicList.indices } ?: musicList.size - 1
            playMusic(currentIndex)
        }
    }

    private fun showMusicList() {
        musicListContainer.removeAllViews()

        for ((index, music) in musicList.withIndex()) {
            val musicItemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 20, 20, 20)
                setBackgroundColor(0xFFE0E0E0.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
            }

            val iconView = TextView(this).apply {
                text = "▶︎"
                textSize = 20f
                setPadding(0, 0, 20, 0)
            }

            val titleView = TextView(this).apply {
                text = "${music.title} - ${music.artist}"
                textSize = 16f
                setTextColor(0xFF000000.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            musicItemLayout.addView(iconView)
            musicItemLayout.addView(titleView)

            musicItemLayout.setOnClickListener {
                if (mediaPlayer?.isPlaying == true && currentMusicUri == music.uri) {
                    mediaPlayer?.pause()
                    iconView.text = "▶︎"
                    playPauseButton.text = "▶"
                } else {
                    currentIndex = index
                    isShuffleMode = false
                    playMusic(currentIndex)
                    currentPlayingIconView?.text = "▶︎"
                    iconView.text = "⏸︎"
                    currentPlayingIconView = iconView
                }
            }

            musicListContainer.addView(musicItemLayout)
        }

        if (musicList.isEmpty()) {
            Toast.makeText(this, "Nenhuma música encontrada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playMusic(index: Int) {
        if (isShuffleMode && index !in randomQueue.indices) return
        if (!isShuffleMode && index !in musicList.indices) return

        val music = if (isShuffleMode) musicList[randomQueue[index]] else musicList[index]

        try {
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, music.uri)
                prepare()
                start()
                setOnCompletionListener {
                    nextButton.performClick()
                }
            }

            seekBar.max = mediaPlayer?.duration ?: 0

            updateSeekBarRunnable = object : Runnable {
                override fun run() {
                    if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                        seekBar.progress = mediaPlayer!!.currentPosition
                        handler.postDelayed(this, 500)
                    }
                }
            }
            handler.post(updateSeekBarRunnable!!)

            currentMusicUri = music.uri
            miniPlayer.visibility = View.VISIBLE
            nowPlayingText.text = "${music.title} - ${music.artist}"
            playPauseButton.text = "⏸︎"

        } catch (e: IOException) {
            Toast.makeText(this, "Erro ao tocar: ${music.title}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun checkPermissionsAndLoadMusic() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadMusic()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadMusic() {
        val tempList = mutableListOf<Music>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val title = it.getString(titleIndex)
                val artist = it.getString(artistIndex)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                tempList.add(Music(title, artist, contentUri))
            }
        }

        musicList = tempList
        showMusicList()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateSeekBarRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
