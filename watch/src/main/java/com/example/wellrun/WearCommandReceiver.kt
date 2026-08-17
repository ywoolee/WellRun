package com.example.wellrun

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearCommandReceiver : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        Log.d("WellRunReceiver", "신호 수신됨! Path: ${messageEvent.path}")
        val context = applicationContext
        val lbm = LocalBroadcastManager.getInstance(context)

        when (messageEvent.path) {
            "/start_hr" -> {
                val intent = Intent(context, HeartRateService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
            "/stop_hr" -> {
                val intent = Intent(context, HeartRateService::class.java)
                context.stopService(intent)
            }
            "/update_distance" -> {
                val distance = String(messageEvent.data)
                val intent = Intent("com.example.wellrun.DISTANCE_UPDATE")
                intent.putExtra("distance", distance)
                lbm.sendBroadcast(intent)
            }
            "/update_pace" -> {
                val pace = String(messageEvent.data)
                val intent = Intent("com.example.wellrun.PACE_UPDATE")
                intent.putExtra("pace", pace)
                lbm.sendBroadcast(intent)
            }
            // ✨ 일시정지 및 재개 신호 추가
            "/pause_run" -> {
                lbm.sendBroadcast(Intent("com.example.wellrun.PAUSE_RUN"))
                Log.d("WellRunReceiver", "일시정지 신호 내부 전달")
            }
            "/resume_run" -> {
                lbm.sendBroadcast(Intent("com.example.wellrun.RESUME_RUN"))
                Log.d("WellRunReceiver", "재개 신호 내부 전달")
            }
        }
    }
}