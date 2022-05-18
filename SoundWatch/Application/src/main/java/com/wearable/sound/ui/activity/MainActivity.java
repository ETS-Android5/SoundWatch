/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wearable.sound.ui.activity;

import static com.wearable.sound.utils.Constants.*;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.wearable.sound.R;
import com.wearable.sound.datalayer.DataLayerListenerService;
import com.wearable.sound.models.SoundNotification;
import com.wearable.sound.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset to
 * the paired wearable
 */
public class MainActivity extends AppCompatActivity
        implements CapabilityClient.OnCapabilityChangedListener {

    private static final String TAG = "MainActivity";

    /**
     * The {@code FirebaseAnalytics} used to record screen views.
     */
    // [START declare_analytics]
    private FirebaseAnalytics mFirebaseAnalytics;

    // [END declare_analytics]

//    /**
//     * Different Mode configuration
//     */
//    public static final int HIGH_ACCURACY_MODE = 1;

    public static final boolean TEST_MODEL_LATENCY = false;
    public static final boolean TEST_E2E_LATENCY = false;

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String AUDIO_PREDICTION_PATH = "/audio-prediction";
    private static final String SEND_FOREGROUND_SERVICE_STATUS_FROM_PHONE_PATH = "/SEND_FOREGROUND_SERVICE_STATUS_FROM_PHONE_PATH";
    private static final String SEND_LISTENING_STATUS_FROM_PHONE_PATH = "/SEND_LISTENING_STATUS_FROM_PHONE_PATH";
    private static final String COUNT_PATH = "/count";
    private static final String COUNT_KEY = "count";
    private static final String MY_PREF = "my_preferences";

    private static final String SOUND_ENABLE_FROM_PHONE_PATH = "/SOUND_ENABLE_FROM_PHONE_PATH";
    public static final String AUDIO_LABEL = "AUDIO_LABEL";
    public static final String FOREGROUND_LABEL = "FOREGROUND_LABEL";
    public static final String WATCH_STATUS_LABEL = "WATCH_STATUS_LABEL";
    public static final String SOUND_PREDICTION_LABEL = "SOUND_PREDICTION_LABEL";

    private ConstraintLayout tutorialLayout;
    private ListView mDataItemList;
    private Button mSendPhotoBtn;
    private Button tutorialBtn;
    private ImageView mThumbView;
    private Bitmap mImageBitmap;
    private View mStartActivityBtn;
    private DataItemAdapter mDataItemListAdapter;

    /**
     * Instead of deprecated AsyncTask API, use java.util.concurrent to run async tasks instead
     * https://stackoverflow.com/questions/58767733/android-asynctask-api-deprecating-in-android-11-what-are-the-alternatives
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();


    // List of all sounds with available notifications (no more high vs low accuracy)
    public List<String> sounds = Arrays.asList(
            CAT_MEOW,
            VEHICLE,
            CAR_HONK,
            DOG_BARK,
            MICROWAVE,
            WATER_RUNNING,
            DOOR_IN_USE,
            BABY_CRY,
            FIRE_SMOKE_ALARM,
            KNOCKING
    );
    public static Map<String, SoundNotification> SOUNDS_MAP = new HashMap<>();
    public static Map<String, Integer> CHECKBOX_MAP = new HashMap<>();

//    public SharedPreferences.OnSharedPreferenceChangeListener autoUpdate;

    {
        // Default: every sound is enabled and not snoozed.
        for (String sound : sounds) {
            SOUNDS_MAP.put(sound, new SoundNotification(sound, true, false));
        }

        CHECKBOX_MAP.put(CAT_MEOW, R.id.cat_meow);
        CHECKBOX_MAP.put(DOG_BARK, R.id.dog_bark);
        CHECKBOX_MAP.put(VEHICLE, R.id.vehicle);
        CHECKBOX_MAP.put(CAR_HONK, R.id.car_honk);
        CHECKBOX_MAP.put(MICROWAVE, R.id.microwave);
        CHECKBOX_MAP.put(WATER_RUNNING, R.id.water_running);
        CHECKBOX_MAP.put(DOOR_IN_USE, R.id.door_in_use);
        CHECKBOX_MAP.put(BABY_CRY, R.id.baby_crying);
        CHECKBOX_MAP.put(FIRE_SMOKE_ALARM, R.id.fire_smoke_alarm);
        CHECKBOX_MAP.put(KNOCKING, R.id.knocking);
    }

    /**
     * Update when the check box gets clicked
     */
    public void onCheckBoxClick(View view) {
        int id = view.getId();
        SoundNotification currentSound = null;
        for (String sound : CHECKBOX_MAP.keySet()) {
            if (CHECKBOX_MAP.get(sound) == id) {
                currentSound = SOUNDS_MAP.get(sound);
            }
        }

        // Change the text according to the text label
        if (currentSound != null) {
            CheckBox checkBox = (CheckBox) view;
            boolean isEnabled = checkBox.isChecked();
            if (checkBox.getText().toString().contains("Snoozed")) {
                // If the sound is currently snoozed
                int snoozeTextIndex = checkBox.getText().toString().indexOf("(Snoozed)");
                String soundLabel = checkBox.getText().toString().substring(0, snoozeTextIndex);
                checkBox.setText(soundLabel);
//                checkBox.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
            currentSound.isEnabled = isEnabled;
            sendSoundEnableMessageToWatch(currentSound);

            // Put current sound value into SharedPreference
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(currentSound.label, currentSound.isEnabled);

            // [FS-Logging]: Log user interaction with the sound checkbox list
//            FirebaseLogging(currentSound.label, String.valueOf(currentSound.isEnabled).charAt(0) + String.valueOf(Instant.now().getEpochSecond()), "sound_type");
            Bundle bundle = new Bundle();
            bundle.putString(SINGLE_SOUND_PARAM, currentSound.label);
            bundle.putBoolean(ENABLE_PARAM, isEnabled);
            fsLogging(CHECKBOX_EVENT, bundle);

            editor.apply();
        }
    }

    public static final String mBroadcastSoundPrediction = "com.wearable.sound.broadcast.soundPrediction";
    public static final String mBroadcastSnoozeSound = "com.wearable.sound.broadcast.snoozeSound";
    public static final String mBroadcastUnsnoozeSound = "com.wearable.sound.broadcast.unsnoozeSound";
    public static final String mBroadcastForegroundService = "com.wearable.sound.broadcast.foregroundService";
    public static final String mBroadcastDisableForegroundService = "com.wearable.sound.broadcast.disableForegroundService";

    private IntentFilter mIntentFilter;

    String[] permissions = new String[]{
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            //Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO,
    };

    /**
     * SocketIO
     */
    public static Socket mSocket;
    //    private static final String SERVER_URL = "http://128.208.49.41:8787";
    private static final String TEST_E2E_LATENCY_SERVER = "http://128.208.49.41:8789";
    private static final String MODEL_LATENCY_SERVER = "http://128.208.49.41:8790";
    private static final String DEFAULT_SERVER = "http://128.208.49.41:8788";

    static {
        String SERVER_URL;
        if (TEST_E2E_LATENCY) {
            SERVER_URL = TEST_E2E_LATENCY_SERVER;
        } else if (TEST_MODEL_LATENCY) {
            SERVER_URL = MODEL_LATENCY_SERVER;
        } else {
            SERVER_URL = DEFAULT_SERVER;
        }
        try {
            mSocket = IO.socket(SERVER_URL);
        } catch (URISyntaxException e) {
            Log.e(TAG, String.valueOf(e));
        }
    }

    private final Emitter.Listener onNewMessage = args -> {
        Log.i(TAG, "Received socket event");
        JSONObject data = (JSONObject) args[0];
        String db;
        String audioLabel;
        String accuracy;
        String recordTime = "";
        try {
            audioLabel = data.getString("label");
            accuracy = data.getString("accuracy");
            db = data.getString("db");
            if (TEST_E2E_LATENCY) {
                recordTime = data.getString("record_time");
            }
        } catch (JSONException e) {
            Log.i(TAG, "JSON Exception failed: " + data);
            return;
        }
        Log.i(TAG, "received sound label from Socket server: " + audioLabel + ", " + accuracy + ", " + db);
        sendAudioLabelToWatch(audioLabel, accuracy, db, recordTime);
    };

    private void askPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // do something?
                return;
            }
        }
    }

    public static boolean isFirst(Context context) {
        final SharedPreferences reader = context.getSharedPreferences(MY_PREF, Context.MODE_PRIVATE);
        final boolean first = reader.getBoolean("is_first", true);
        if (first) {
            final SharedPreferences.Editor editor = reader.edit();
            editor.putBoolean("is_first", false);
            editor.apply();
        }
        return first;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logD(TAG, "onCreate");

        // Ask permissions
        askPermissions();
        if (ARCHITECTURE.equals(PHONE_WATCH_SERVER_ARCHITECTURE)) {
            mSocket.on("audio_label", onNewMessage);
            mSocket.connect();
            //            Toast.makeText(this, "socket connected", Toast.LENGTH_SHORT).show();
        }

        setContentView(R.layout.activity_main);

        // [START shared_app_measurement]
        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        // [END shared_app_measurement]

        // create Bottom Navigation View for switch between tabs

        logD(TAG, "create BottomNavigationView");
        BottomNavigationView bottomNavView = findViewById(R.id.bottom_nav_view);
        bottomNavView.setOnItemSelectedListener(mOnNavigationItemSelectedListener);

        // set Mode
        // setAccuracyMode(HIGH_ACCURACY_MODE);

        // Stores DataItems received by the local broadcaster or from the paired watch.
//        mDataItemListAdapter = new DataItemAdapter(this, android.R.layout.simple_list_item_1);
//        mDataItemList.setAdapter(mDataItemListAdapter);

//        mGeneratorExecutor = new ScheduledThreadPoolExecutor(1);

        SharedPreferences sharedPref = PreferenceManager
                .getDefaultSharedPreferences(this);

        // Create a SharedPreference for checking if the app is opening for the first time
        boolean isFirstTime = isFirst(MainActivity.this);
        if (isFirstTime) {
            bottomNavView.setSelectedItemId(R.id.bottom_navigation_item_help);
            Intent tutorial = new Intent(MainActivity.this, Tutorial.class);
            startActivity(tutorial);
            SharedPreferences.Editor editor = sharedPref.edit();
            for (String sound : sounds) {
                editor.putBoolean(sound, true);

//                FirebaseLogging(sound, "T" + Instant.now().getEpochSecond(), "sound_type");
            }
            editor.apply();
        }

        // Iterate through the sharedPref to retrieve the state of each sound
        StringBuilder soundStatus = new StringBuilder();
        for (String sound : sounds) {
            boolean isEnabled = sharedPref.getBoolean(sound, true);
            SoundNotification currentSound = new SoundNotification(sound, isEnabled, false);
            Log.e(TAG, currentSound.toString());
            sendSoundEnableMessageToWatch(currentSound);
            CheckBox checkBox = findViewById(CHECKBOX_MAP.get(sound));
            checkBox.setChecked(isEnabled);

            soundStatus.append(sound).append("-").append(isEnabled);
        }

        // Create a SharedPreference for root_preferences to update and use value from the setting tab
        boolean isSleepModeOn = sharedPref.getBoolean("foreground_service", false);
        Log.d(TAG, "isSleepModeOn1" + isSleepModeOn);

        if (!isSleepModeOn) {
            Wearable.getMessageClient(MainActivity.this)
                    .sendMessage(FOREGROUND_LABEL, SEND_FOREGROUND_SERVICE_STATUS_FROM_PHONE_PATH, "foreground_enabled".getBytes());
        } else {
            Wearable.getMessageClient(MainActivity.this)
                    .sendMessage(FOREGROUND_LABEL, SEND_FOREGROUND_SERVICE_STATUS_FROM_PHONE_PATH, "foreground_disabled".getBytes());
        }

        // // Turn this to true to reset Preference every time the app open (2/2)
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
//        isSleepModeOn = sharedPref.getBoolean("foreground_service", false);

        // Start the service once by default
        Log.i(TAG, "Starting foreground service first time");
        Intent serviceIntent = new Intent(MainActivity.this, DataLayerListenerService.class);
        serviceIntent.setAction(ACTION.START_FOREGROUND_ACTION);
        ContextCompat.startForegroundService(MainActivity.this, serviceIntent);

        // Add toggle to turn on/off foreground service
//        autoUpdate = mOnSharedPreferenceChangeListener;
        sharedPref.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

        // Register the receiver
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(mBroadcastSoundPrediction);
        mIntentFilter.addAction(mBroadcastSnoozeSound);
        mIntentFilter.addAction(mBroadcastUnsnoozeSound);
        mIntentFilter.addAction(mBroadcastForegroundService);
        registerReceiver(mReceiver, mIntentFilter);

        // [FS-Logging] Log the current settings when the app is first opened
        Bundle bundle = new Bundle();
        bundle.putString(ALL_SOUNDS_PARAM, soundStatus.toString());
        bundle.putBoolean(SLEEP_MODE_PARAM, isSleepModeOn);
        fsLogging("on_create_event", bundle);
    }

    // Preference Listener for Setting -> Foreground -> Enable/Disable Foreground Service
    private final SharedPreferences.OnSharedPreferenceChangeListener
            mOnSharedPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    Intent serviceIntent = new Intent(MainActivity.this, DataLayerListenerService.class);
                    if (key.equals("foreground_service")) {
                        boolean isSleepModeOn = sharedPreferences.getBoolean("foreground_service", false);
                        if (!isSleepModeOn) {
                            Log.i(TAG, "Starting foreground service in main (Sleep Mode OFF)");
                            // [FS-Logging] Starting foreground service in main

                            mSocket.on("audio_label", onNewMessage);
                            mSocket.connect();

                            serviceIntent.setAction(ACTION.START_FOREGROUND_ACTION);
                            ContextCompat.startForegroundService(MainActivity.this, serviceIntent);

                            Wearable.getMessageClient(MainActivity.this)
                                    .sendMessage(FOREGROUND_LABEL, SEND_FOREGROUND_SERVICE_STATUS_FROM_PHONE_PATH, "foreground_enabled".getBytes());
//                          Toast.makeText(MainActivity.this, "Foreground service started.", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.i(TAG, "Stopping foreground service in main (Sleep Mode ON)");
                            // [FS-Logging] Stopping foreground service in main

                            serviceIntent.setAction(ACTION.STOP_FOREGROUND_ACTION);
                            mSocket.disconnect();
                            mSocket.off("audio_label", onNewMessage);

                            ContextCompat.startForegroundService(MainActivity.this, serviceIntent);

                            Wearable.getMessageClient(MainActivity.this)
                                    .sendMessage(FOREGROUND_LABEL, SEND_FOREGROUND_SERVICE_STATUS_FROM_PHONE_PATH, "foreground_disabled".getBytes());
//                          Toast.makeText(MainActivity.this, "Foreground service stopped.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    // enable this for Listening MODE: 1 out of 3
//                    else if (key.equals("listening_status")) {
//                        boolean isListeningModeOn = sharedPreferences.getBoolean("listening_status", false);
//                        if (isListeningModeOn) {
//                            Wearable.getMessageClient(MainActivity.this)
//                                    .sendMessage(WATCH_STATUS_LABEL, SEND_LISTENING_STATUS_FROM_PHONE_PATH, "start_listening".getBytes());
//                        } else {
//                            Wearable.getMessageClient(MainActivity.this)
//                                    .sendMessage(WATCH_STATUS_LABEL, SEND_LISTENING_STATUS_FROM_PHONE_PATH, "stop_listening".getBytes());
//                        }
//                    }
                }
            };

    // Item Selected Listener for Bottom Navigation Bar
    private final NavigationBarView.OnItemSelectedListener
            mOnNavigationItemSelectedListener =
            item -> {
                TextView titleView = findViewById(R.id.title_text);
                ScrollView scrollView = findViewById(R.id.scroll_view);
                FrameLayout frameLayout = findViewById(R.id.fragment_container);
                TextView instructionalView = findViewById(R.id.instruction_text);
                Fragment fragment = null;
                int itemId = item.getItemId();
                if (itemId == R.id.bottom_navigation_item_home) {
                    titleView.setText(R.string.soundwatch);
                    fragment = null;
                    frameLayout.setVisibility(View.GONE);
                    instructionalView.setVisibility(View.VISIBLE);
                    scrollView.setVisibility(View.VISIBLE);
                } else if (itemId == R.id.bottom_navigation_item_about) {
                    titleView.setText(R.string.about);
                    fragment = new ScrollingFragment();
                    frameLayout.setVisibility(View.VISIBLE);
                    instructionalView.setVisibility(View.GONE);
                    scrollView.setVisibility(View.GONE);
                } else if (itemId == R.id.bottom_navigation_item_help) {
                    titleView.setText("");
                    fragment = new HelpFragment();
                    frameLayout.setVisibility(View.VISIBLE);
                    instructionalView.setVisibility(View.GONE);
                    scrollView.setVisibility(View.GONE);
                } else if (itemId == R.id.bottom_navigation_item_setting) {
                    titleView.setText(R.string.setting);
                    fragment = new SettingsFragment();
                    frameLayout.setVisibility(View.VISIBLE);
                    instructionalView.setVisibility(View.GONE);
                    scrollView.setVisibility(View.GONE);
                }
                loadFragment(fragment);
                return true;
            };


    @Override
    public void onDestroy() {
        super.onDestroy();
        Intent serviceIntent = new Intent(this, DataLayerListenerService.class);
        stopService(serviceIntent);
        mSocket.disconnect();
        mSocket.off("audio_label", onNewMessage);

        // [FS-Logging]: Log when the app shut off + all current settings
        fsLogging("on_destroy", new Bundle());
//        for (String sound : sounds) {
//            FirebaseLogging(sound, "F" + Instant.now().getEpochSecond(), "sound_type");
//        }
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);

        askForParticipantId();

        // [FS-Logging]: Log when the app is resumed + all current settings
        fsLogging("on_resume_event", new Bundle());
    }

    // Receive message from watch on which sound to snooze, and watch status
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), mBroadcastSoundPrediction)) {
                mDataItemListAdapter.add(new Event("Sound prediction", intent.getStringExtra(SOUND_PREDICTION_LABEL)));
            } else if (Objects.equals(intent.getAction(), mBroadcastSnoozeSound)) {
                CheckBox checkBox = getCheckboxFromAudioLabel(Objects.requireNonNull(intent.getStringExtra(AUDIO_LABEL)));
                Log.i(TAG, "Getting checkbox main: " + checkBox);

                SoundNotification soundNotification = SOUNDS_MAP.get(intent.getStringExtra(AUDIO_LABEL));
                if (soundNotification != null) { soundNotification.isSnoozed = true; }
                if (checkBox == null) { return;}
                if (!checkBox.getText().toString().contains("Snoozed")) {
                    checkBox.setText(MessageFormat.format("{0} (Snoozed)", checkBox.getText()));
                }
                checkBox.setChecked(false);

                // [FS-Logging]: Snooze sound message from watch
                Bundle bundle = new Bundle();
                bundle.putString(SINGLE_SOUND_PARAM, intent.getStringExtra(AUDIO_LABEL));
                bundle.putBoolean(SNOOZE_PARAM, true);
                fsLogging(SNOOZE_EVENT, bundle);

//                checkBox.setCompoundDrawablesWithIntrinsicBounds(0,0 , android.R.drawable.ic_lock_silent_mode, 0);
            } else if (intent.getAction().equals(mBroadcastUnsnoozeSound)) {
                Log.i(TAG, "Phone received unsnoozed");
                CheckBox checkBox = getCheckboxFromAudioLabel(Objects.requireNonNull(intent.getStringExtra(AUDIO_LABEL)));
                SoundNotification soundNotification = SOUNDS_MAP.get(intent.getStringExtra(AUDIO_LABEL));
                if (soundNotification != null) { soundNotification.isSnoozed = false; }
                if (checkBox == null) { return; }
                if (checkBox.getText().toString().contains("Snoozed")) {
                    // If the sound is currently snoozed
                    int snoozeTextIndex = checkBox.getText().toString().indexOf("(Snoozed)");
                    String soundLabel = checkBox.getText().toString().substring(0, snoozeTextIndex);
                    checkBox.setText(soundLabel);
                    checkBox.setChecked(true);
//                    checkBox.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
                // [FS-Logging]: Unsnooze sound message from watch
                Bundle bundle = new Bundle();
                bundle.putString(SINGLE_SOUND_PARAM, intent.getStringExtra(AUDIO_LABEL));
                bundle.putBoolean(SNOOZE_PARAM, false);
                fsLogging(SNOOZE_EVENT, bundle);

            } else if (intent.getAction().equals(mBroadcastForegroundService)) {
                // [FS-Logging]: foreground service message

                Log.i(TAG, "Phone received Watch Status: " + intent.getStringExtra(FOREGROUND_LABEL));
                if (intent.getStringExtra(FOREGROUND_LABEL).equals("watch_start_record")) {
                    Toast.makeText(MainActivity.this, "Smartwatch starts listening..", Toast.LENGTH_SHORT).show();
                } else if (intent.getStringExtra(FOREGROUND_LABEL).equals("watch_stop_record")) {
                    Toast.makeText(MainActivity.this, "Smartwatch stops listening", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    // Get the Checkout given audio label
    public CheckBox getCheckboxFromAudioLabel(String audioLabel) {
        return CHECKBOX_MAP.containsKey(audioLabel) ? findViewById(CHECKBOX_MAP.get(audioLabel)) : null;
    }

    @Override
    public void onPause() {
        super.onPause();

        // [FS-Logging]: Log if the app is put on paused
        fsLogging("on_pause_event", new Bundle());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);

        // [FS-Logging]: Log if the app is put on stop
        fsLogging("on_stop_event", new Bundle());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            mImageBitmap = (Bitmap) extras.get("data");
            mThumbView.setImageBitmap(mImageBitmap);
        }
    }

    @Override
    public void onCapabilityChanged(final CapabilityInfo capabilityInfo) {
        logD(TAG, "onCapabilityChanged: " + capabilityInfo);
        // [FS-Logging] Log on capability change
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.VALUE, capabilityInfo.toString());
        fsLogging("on_capability_changed", bundle);

        mDataItemListAdapter.add(new Event("onCapabilityChanged", capabilityInfo.toString()));
    }

    @WorkerThread
    private void sendMessageWithData(String node, String title, byte[] data) {
        Log.i(TAG, "Node: " + node + "\n title: " + title + "\n data: " + Arrays.toString(data));

        Task<Integer> sendMessageTask =
                Wearable.getMessageClient(this)
                        .sendMessage(node, title, data);

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            Integer result = Tasks.await(sendMessageTask);
            logD(TAG, "Message sent: " + result);
        } catch (ExecutionException exception) {
            Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            Log.e(TAG, "Interrupt occurred: " + exception);
        }
    }

    @WorkerThread
    private void sendStartActivityMessage(String node) {
        Task<Integer> sendMessageTask =
                Wearable.getMessageClient(this).sendMessage(node, START_ACTIVITY_PATH, new byte[0]);

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            Integer result = Tasks.await(sendMessageTask);
            logD(TAG, "Message sent: " + result);

        } catch (ExecutionException exception) {
            Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            Log.e(TAG, "Interrupt occurred: " + exception);
        }
    }

    @WorkerThread
    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();

        Task<List<Node>> nodeListTask =
                Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            List<Node> nodes = Tasks.await(nodeListTask);

            for (Node node : nodes) {
                results.add(node.getId());
            }

        } catch (ExecutionException exception) {
            Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            Log.e(TAG, "Interrupt occurred: " + exception);
        }

        return results;
    }

    public void askForParticipantId() {
        SharedPreferences sharedPref = PreferenceManager
                .getDefaultSharedPreferences(this);

        // Retrieve participant id from the settings
        String pidKey = getResources().getString(R.string.pid_pref_key);
        if (sharedPref.getString(pidKey, null) == null) {
            // display a dialog to alert user to type in their participant id:
            AlertDialog.Builder pIdDialog = new AlertDialog.Builder(MainActivity.this);
            pIdDialog.setMessage("Please go to Settings tab and type in your participant ID.\n" +
                            "If you have not received it, please contact SoundWatch team.")
                    .setTitle("Participant ID Required!")
                    .setPositiveButton("OK", (dialogInterface, i) -> {
                        // simply dismiss
                        dialogInterface.dismiss();
                    })
                    .create()
                    .show();
        }
    }


    /**
     * A View Adapter for presenting the Event objects in a list
     */
    private static class DataItemAdapter extends ArrayAdapter<Event> {

        private final Context mContext;

        public DataItemAdapter(Context context, int unusedResource) {
            super(context, unusedResource);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                logD(TAG, "convertView is null");
                holder = new ViewHolder();
                LayoutInflater inflater =
                        (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(android.R.layout.two_line_list_item, null);
                convertView.setTag(holder);
                holder.text1 = convertView.findViewById(android.R.id.text1);
                holder.text2 = convertView.findViewById(android.R.id.text2);
            } else {
                logD(TAG, "convertView is " + convertView.getTag());
                holder = (ViewHolder) convertView.getTag();
            }
            Event event = getItem(position);
            holder.text1.setText(event.title);
            holder.text2.setText(event.text);
            return convertView;
        }

        private static class ViewHolder {
            TextView text1;
            TextView text2;
        }
    }

    private static class Event {
        String title;
        String text;

        public Event(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    /**
     * loading fragment into FrameLayout
     *
     * @param fragment a new fragment
     */
    private void loadFragment(Fragment fragment) {
        // switching fragment
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    /* -------------------------------------------------------------------------
        Asynchronous Tasks
       -----------------------------------------------------------------------*/
    /**
     * Sending enabled sounds set in phone to the wearable
     */
    private void sendSoundEnableMessageToWatch(SoundNotification soundNotification) {
        String data = soundNotification.label + "," + soundNotification.isEnabled + "," + soundNotification.isSnoozed;
        executorService.execute(() -> {
            Collection<String> nodes = getNodes();
            Log.i(TAG, "Sending enabled data to watch " + nodes.size());
            for (String node : nodes) {
                Log.i(TAG, ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + ":Sending enabled data from phone: " + data);
                sendMessageWithData(node, SOUND_ENABLE_FROM_PHONE_PATH, data.getBytes());
            }
        });
    }

    /**
     * To send audio labels of predictions to the wearable
     */
    private void sendAudioLabelToWatch(String prediction, String confidence, String db, String recordTime) {
        executorService.execute(() -> {
            Collection<String> nodes = getNodes();
            String result = prediction + "," + confidence + "," + LocalTime.now() + "," + db + "," + recordTime;
            Log.i(TAG, "Sending audio label to watch " + nodes.size());
            for (String node : nodes) {
                Log.i(TAG, "Sending sound prediction: " + result);
                sendMessageWithData(node, AUDIO_PREDICTION_PATH, result.getBytes());
            }
        });
    }


    /* -------------------------------------------------------------------------
        Logging
       -----------------------------------------------------------------------*/

    /**
     * To log with Firebase
     */
//    private void FirebaseLogging(String id, String name, String content_type) {
//        Bundle bundle = new Bundle();
//        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id);
//        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, name);
//        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, content_type);
//        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
//    }

    /**
     * Take in a bundle and event type, log to Firebase and also write to external file
     */
    private void fsLogging(String eventName, Bundle bundle) {
        SharedPreferences sharedPref = PreferenceManager
                .getDefaultSharedPreferences(this);

        // always add participant id and current shard pref
        String pidKey = getResources().getString(R.string.pid_pref_key);
        bundle.putString(pidKey, sharedPref.getString(pidKey, Build.MODEL));
        String dbLevelKey = getResources().getString(R.string.db_threshold_pref_key);
        bundle.putInt(dbLevelKey, sharedPref.getInt(dbLevelKey, 40));
        String fgPrefKey = getResources().getString(R.string.foreground_pref_key);
        bundle.putBoolean(fgPrefKey, sharedPref.getBoolean(fgPrefKey, false));


        // log with firebase
        mFirebaseAnalytics.logEvent(eventName, bundle);

        // write to external file
    }

    /**
     * As simple wrapper around Log.d
     */
    private static void logD(String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }
}
