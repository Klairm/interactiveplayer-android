package com.github.klairm.interactiveplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.klairm.interactiveplayer.databinding.ActivityMainBinding
import com.github.klairm.interactiveplayer.entities.Choice
import com.github.klairm.interactiveplayer.entities.VideoMoment
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var binding: ActivityMainBinding
    private lateinit var playerView: PlayerView
    private lateinit var overlayTextView: TextView
    private lateinit var buttonsContainer: LinearLayout
    private val actionsQueue = mutableListOf<Pair<Long, String>>()
    private val PICK_VIDEO_REQUEST = 1
    private lateinit var player: ExoPlayer
    private val activeMoments = mutableListOf<VideoMoment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playerView = binding.playerView
        overlayTextView = binding.overlayTextView
        buttonsContainer = binding.buttonsContainer

        selectVideoFromDevice()
    }

    private fun selectVideoFromDevice() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "video/*"
        startActivityForResult(Intent.createChooser(intent, "Selecciona un video"), PICK_VIDEO_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { videoUri ->
                playVideo(videoUri)
            }
        }
    }

    private fun playVideo(videoUri: Uri) {
        player = ExoPlayer.Builder(this).build()
        player.setMediaItem(MediaItem.fromUri(videoUri))
        playerView.player = player
        player.prepare()
        player.playWhenReady = true

        val json = findJSONForVideo()
        json?.let {
            parseMoments(it)
            startCheckingTimestamps()
        }
    }

    private fun findJSONForVideo(): JSONObject? {
        val inputStream = resources.openRawResource(R.raw.timestamps)
        val text = inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text)
    }


    
    
    
    
    
    
    
    
private fun parseMoments(json: JSONObject) {
    val videos = json
        .optJSONObject("jsonGraph")
        ?.optJSONObject("videos") ?: return
// I only tested with one json from one media so I cant be sure there wont be more than one ID in the videos object
    val videoIds = videos.keys()
    while (videoIds.hasNext()) {
        val videoId = videoIds.next()
        val momentsBySegment = videos
            .optJSONObject(videoId)
            ?.optJSONObject("interactiveVideoMoments")
            ?.optJSONObject("value")
            ?.optJSONObject("momentsBySegment") ?: continue

        val segmentKeys = momentsBySegment.keys()
        while (segmentKeys.hasNext()) {
            val segmentKey = segmentKeys.next()
            val momentsArray = momentsBySegment.optJSONArray(segmentKey) ?: continue

            for (i in 0 until momentsArray.length()) {
                val moment = momentsArray.optJSONObject(i) ?: continue
                val start = moment.optLong("startMs", -1)
                val end = moment.optLong("endMs", -1)
                val id = moment.optString("id", "")
                val type = moment.optString("type", "")
                val bodyText = moment.optString("bodyText", null)

                val choicesList = mutableListOf<Choice>()
                val choicesArray = moment.optJSONArray("choices")
                if (choicesArray != null) {
                    for (j in 0 until choicesArray.length()) {
                        val choiceObj = choicesArray.optJSONObject(j) ?: continue
                        val choiceId = choiceObj.optString("id", "")
                        val choiceText = choiceObj.optString("text", "")
                        val choiceSegmentId = choiceObj.optString("segmentId", null)
                        choicesList.add(Choice(choiceId, choiceText, choiceSegmentId))
                    }
                }

                if (start >= 0 && end >= 0) {
                    actionsQueue.add(Pair(start, "show:$id"))
                }

                activeMoments.add(
                    VideoMoment(
                        id,
                        start,
                        end,
                        type,
                        bodyText,
                        choicesList.ifEmpty { null }
                    )
                )
            }
        }
    }
    actionsQueue.sortBy { it.first }
}






    private fun startCheckingTimestamps() {
        handler.post(object : Runnable {
            override fun run() {
                val currentPosition = player.currentPosition
                val iterator = activeMoments.iterator()
                while (iterator.hasNext()) {
                    val moment = iterator.next()
                    if (currentPosition in moment.startMs..moment.endMs) {
                        showMoment(moment)
                    } else if (currentPosition > moment.endMs) {
                        hideMoment(moment)
                 iterator.remove()
                    }
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun showMoment(moment: VideoMoment) {
        overlayTextView.text = moment.bodyText ?: ""
        overlayTextView.visibility = View.VISIBLE
        //Log.d("MainActivity:MOMENT", "Showing moment: $moment")
        buttonsContainer.removeAllViews()
        playerView.isClickable = false

        moment.choices?.forEach { choice ->
            val button = Button(this)
            button.text = choice.text

            button.setOnClickListener {
                Log.d("MainActivity:CHOICE", "Choice clicked: $choice")
                choice.segmentId?.let { segId ->

                    Log.d("MainActivity:JUMP", "Jump to segment: $segId")
                    jumpToSegment(segId)
                    playerView.isClickable = true
                }
            }
            buttonsContainer.addView(button)
        }
        buttonsContainer.visibility = if (moment.choices.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun hideMoment(moment: VideoMoment) {
        overlayTextView.visibility = View.GONE
        buttonsContainer.visibility = View.GONE
    }

    private fun jumpToSegment(segmentId: String) {
        val moment = activeMoments.find { it.id == segmentId }
        moment?.let {
          buttonsContainer.removeAllViews()

            player.seekTo(it.startMs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)
        if (this::player.isInitialized) player.release()
    }
}
