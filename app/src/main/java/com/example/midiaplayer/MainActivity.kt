package com.example.midiaplayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.app.NotificationCompat
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

    private val CHANNEL_ID = "music_channel"
    private val NOTIFICATION_ID = 1

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

        createNotificationChannel()

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
                if (isShuffleMode) {
                    currentIndex = if (currentIndex - 1 < 0) randomQueue.size - 1 else currentIndex - 1
                    playMusic(randomQueue[currentIndex])
                } else {
                    currentIndex = if (currentIndex - 1 < 0) musicList.size - 1 else currentIndex - 1
                    playMusic(currentIndex)
                }
            }
        }

        nextButton.setOnClickListener {
            if (musicList.isNotEmpty()) {
                if (isShuffleMode) {
                    currentIndex = (currentIndex + 1) % randomQueue.size
                    playMusic(randomQueue[currentIndex])
                } else {
                    currentIndex = (currentIndex + 1) % musicList.size
                    playMusic(currentIndex)
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        checkPermissionsAndLoadMusic()
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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadMusic()
            } else {
                Toast.makeText(this, "Permissão negada para acessar músicas", Toast.LENGTH_SHORT).show()
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

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
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
        displayMusicList(musicList)
    }

    private fun displayMusicList(musicList: List<Music>) {
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

            showNotification(music) // NOTIFICAÇÃO ATUALIZADA

        } catch (e: IOException) {
            Toast.makeText(this, "Erro ao tocar: ${music.title}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Música em reprodução"
            val descriptionText = "Notificações de música tocando"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(music: Music) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tocando agora")
            .setContentText("${music.title} - ${music.artist}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun hideNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateSeekBarRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.release()
        mediaPlayer = null
        hideNotification()
    }
}
