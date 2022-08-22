package com.example.gfnqueuetracker;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.JsonReader;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Deque;
import java.util.LinkedList;

public class GFNTrackerService extends Service {
    public static final String BROADCAST_ACTION = "com.example.gfnqueuetracker.BROADCAST_ACTION";
    public static final String BROADCAST_COUNT_KEY = "com.example.gfnqueuetracker.BROADCAST_COUNT_KEY";
    public static final String BROADCAST_MESSAGE_KEY = "com.example.gfnqueuetracker.BROADCAST_MESSAGE_KEY";
    public static final String BROADCAST_HISTORY_KEY = "com.example.gfnqueuetracker.BROADCAST_HISTORY_KEY";

    public static final String ACTION_START = "com.example.gfnqueuetracker.ACTION_START";
    public static final String ACTION_STOP = "com.example.gfnqueuetracker.ACTION_STOP";

    private Thread serviceThread;
    private ServiceHandler serviceHandler;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        private final Service cur_service;
        private Deque<String> history;
        private static final int MAX_HISTORY_COUNT = 10;
        public ServiceHandler(Looper looper, Service cur_service) {
            super(looper);
            this.cur_service = cur_service;
            this.history = new LinkedList<>();
        }
        public void updateHistory(String new_count) {
            if (history.size() == 0) {
                history.addFirst(new_count);
            } else if (!new_count.equals(history.getFirst())) {
                history.addFirst(new_count);
            }
            if (history.size() > MAX_HISTORY_COUNT) {
                history.removeLast();
            }
        }
        @RequiresApi(api = Build.VERSION_CODES.O)
        public String getHistoryString() {
            return String.join(", ", history);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void handleMessage(Message msg) {
            // read input
            Intent receive_intent = (Intent) msg.obj;
            String url_str = receive_intent.getStringExtra(getString(R.string.key_setting_url));
            String alert_count_str = receive_intent.getStringExtra(getString(R.string.key_setting_alertcount));
            Boolean vibrate_alarm = Boolean.parseBoolean(receive_intent.getStringExtra(getString(R.string.key_setting_vibrate)));
            int alert_count = Integer.parseInt(alert_count_str);

            // make client
            URL url = null;
            try {
                url = new URL(url_str);
            } catch (MalformedURLException e) {
                stopSelf(msg.arg1);
                return;
            }
            GFNTrackerClient gfnclient = new GFNTrackerClient(url, cur_service);

            // make foreground service
            Intent f_intent = new Intent(cur_service, MainActivity.class);
            PendingIntent pending_intent = PendingIntent.getActivity(
                    cur_service, 0, f_intent, PendingIntent.FLAG_UPDATE_CURRENT
            );
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    cur_service, MainActivity.CHANNEL_DEFAULT_ID)
                    .setSmallIcon(R.drawable.notif_icon)
                    .setContentTitle("Current queue count")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pending_intent)
                    .setContentText("" + url_str + " || " + alert_count_str);
            NotificationManagerCompat notif_manager = NotificationManagerCompat.from(cur_service);
            cur_service.startForeground(msg.arg1, builder.build());

            boolean is_alert_notified = false;

            // main loop
            while (true) {
                GFNTrackerClient.Response response;
                int count;
                try {
                    response = gfnclient.getQueueCount();
                } catch (IOException e) {
                    Toast.makeText(cur_service, "Error: " + e.toString(), Toast.LENGTH_LONG).show();
                    stopSelf(msg.arg1);
                    return;
                }

                String count_str = String.valueOf(response.cnt);
                updateHistory(count_str);

                // notification to track
                builder.setContentText(count_str);
                notif_manager.notify(msg.arg1, builder.build());

                // send broadcast
                Intent b_intent = new Intent();
                b_intent.setAction(BROADCAST_ACTION);
                b_intent.putExtra(BROADCAST_COUNT_KEY, count_str);
                b_intent.putExtra(BROADCAST_MESSAGE_KEY, response.msg);
                b_intent.putExtra(BROADCAST_HISTORY_KEY, getHistoryString());
                sendBroadcast(b_intent);

                if (!is_alert_notified && response.cnt < alert_count) {
                    // alert user of reaching alert_count
                    is_alert_notified = true;
                    Toast.makeText(cur_service, getString(R.string.str_alert_achieved), Toast.LENGTH_SHORT);
                    Intent a_intent = new Intent(cur_service, MainActivity.class);
                    PendingIntent p_a_intent = PendingIntent.getActivity(
                            cur_service, 0, a_intent, PendingIntent.FLAG_UPDATE_CURRENT
                    );
                    NotificationCompat.Builder a_builder = new NotificationCompat.Builder(
                            cur_service, MainActivity.CHANNEL_HIGH_ID)
                            .setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle(getString(R.string.str_alert_achieved))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(p_a_intent)
                            .setContentText(
                                    String.format(
                                            getString(R.string.str_alert_count),
                                            alert_count_str
                                    )
                            )
                            .setColor(getColor(R.color.green));
                    notif_manager.notify(msg.arg1 + 100, a_builder.build());

                    if (vibrate_alarm) {
                        // do vibrate
                        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            //deprecated in API 26
                            v.vibrate(2000);
                        }
                    }
                }

                if (response.cnt <= 0) {
                    break;
                }

                // sleep.
                // Actually can persist update without sleep,
                // but that might eat up too much resources, though.
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // Quit. This is to have a way to stop the thread.
                    stopSelf(msg.arg1);
                    return;
                }
            }

            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        // make new thread for service
        HandlerThread thread = new HandlerThread("GFNTrackerServiceThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        serviceThread = thread;
        serviceHandler = new ServiceHandler(thread.getLooper(), this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_START)) {
            Message msg = serviceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.obj = intent;

            // make foreground service
            serviceHandler.sendMessage(msg);
        } else if (intent.getAction().equals(ACTION_STOP)) {
            serviceThread.interrupt();
            stopForeground(true);
            stopSelf();
        }
        // otherwise ignore.

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}

class GFNTrackerClient {
    private final URL url;
    private final Context context;

    public final class Response {
        public int cnt;
        public String msg;
        public Response(int cnt, String msg) {
            this.cnt = cnt;
            this.msg = msg;
        }
    }

    GFNTrackerClient(URL url, Context context) {
        this.url = url;
        this.context = context;
    }
    public Response getQueueCount() throws IOException {
        try (InputStream is = url.openStream()) {
            JsonReader jr = new JsonReader(new InputStreamReader(is));
            int cnt = 0;
            String msg = "";

            jr.beginObject();
            while (jr.hasNext()) {
                String key = jr.nextName();
                if (key.equals("count"))
                    cnt = jr.nextInt();
                else if (key.equals("message"))
                    msg = jr.nextString();
                else
                    jr.skipValue();
            }
            jr.endObject();

            return new Response(cnt, msg);
        }
    }
}