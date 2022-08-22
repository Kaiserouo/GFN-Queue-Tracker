package com.example.gfnqueuetracker;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class MonitorActivity extends AppCompatActivity {
    private BroadcastReceiver br;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        private final Handler hdl;
        private final AppCompatActivity activity;
        MyBroadcastReceiver(Handler hdl, AppCompatActivity activity) {
            this.hdl = hdl;
            this.activity = activity;
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onReceive(Context context, Intent intent) {
            hdl.post(() -> {
                TextView tv_cnt = activity.findViewById(R.id.textView_count);
                TextView tv_msg = activity.findViewById(R.id.textView_message);
                TextView tv_history = activity.findViewById(R.id.textView_history);
                String count = intent.getStringExtra(GFNTrackerService.BROADCAST_COUNT_KEY);
                String msg = intent.getStringExtra(GFNTrackerService.BROADCAST_MESSAGE_KEY);
                String history = intent.getStringExtra(GFNTrackerService.BROADCAST_HISTORY_KEY);
                if (count != null) {
                    tv_cnt.setText(count);
                }
                if (msg != null) {
                    tv_msg.setText(msg);
                }
                if (history != null) {
                    tv_history.setText(history);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        Handler hdl = new Handler(getMainLooper());

        br = new MyBroadcastReceiver(hdl, this);
        IntentFilter intent_filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        intent_filter.addAction(GFNTrackerService.BROADCAST_ACTION);
        registerReceiver(br, intent_filter);
    }

    public void onClickStop(View view) {
        Intent intent = new Intent(this, GFNTrackerService.class);
        intent.setAction(GFNTrackerService.ACTION_STOP);
        startService(intent);

        startActivity(new Intent(this, SettingActivity.class));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(br);
    }

    @Override
    public void onBackPressed() {
        // do absolutely nothing
    }
}

