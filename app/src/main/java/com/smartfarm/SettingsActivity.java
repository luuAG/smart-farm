package com.smartfarm;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import cz.msebera.android.httpclient.Header;

public class SettingsActivity extends AppCompatActivity {
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;
    DataSnapshot setValueSnapshot, sensorSnapshot, relaySnapshot;
    // data
    static final AtomicReference<Long> humid1 = new AtomicReference<>(0L);
    static final AtomicReference<Long> humid2 = new AtomicReference<>(0L);
    static final AtomicReference<Long> humid3 = new AtomicReference<>(0L);
    static final AtomicReference<Long> temp1 = new AtomicReference<>(0L);
    static final AtomicReference<Long> temp2 = new AtomicReference<>(0L);
    static final AtomicReference<Long> temp3 = new AtomicReference<>(0L);
    Long humidThreshold;
    Boolean relay1, relay2, relay3, relay4, mode;
    // UI
    ProgressBar progressBar_humid1, progressBar_humid2, progressBar_humid3;
    TextView txt_humid1, txt_humid2, txt_humid3, txt_temp1, txt_temp2, txt_temp3;
    EditText humidThresholdEditText;
    MaterialButton syncButton;
    // Scheduler
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);;
    ScheduledFuture<?> syncJobHandler = null;
    Runnable syncJob;
    // HTTP
    private static SyncHttpClient client = new SyncHttpClient ();
    static final String thingSpeakPostAPI = "https://api.thingspeak.com/update.json";
    RequestParams rp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FF03DAC5")));
        }
        // binding UI
        syncButton = findViewById(R.id.syncButton);
        progressBar_humid1 = findViewById(R.id.progressBar1);
        progressBar_humid2 = findViewById(R.id.progressBar2);
        progressBar_humid3 = findViewById(R.id.progressBar3);
        txt_humid1 = findViewById(R.id.textView7);
        txt_humid2 = findViewById(R.id.textView8);
        txt_humid3 = findViewById(R.id.textView10);
        txt_temp1 = findViewById(R.id.temp1);
        txt_temp2 = findViewById(R.id.temp2);
        txt_temp3 = findViewById(R.id.temp3);
        humidThresholdEditText = findViewById(R.id.editTextNumber);
        humidThresholdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void afterTextChanged(Editable editable) {
                if(editable.toString().equals("")) {
                    humidThresholdEditText.setText("0");
                    humidThreshold = 0L;
                }
                else{
                    humidThreshold = Long.valueOf(editable.toString());
                }
                humidThresholdEditText.setSelection(editable.toString().length());
                if (setValueSnapshot != null){
                    setValueSnapshot.getRef().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            setValueSnapshot.getRef().child("Soil").setValue(humidThreshold.toString());
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(SettingsActivity.this, "Fail to update data.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });

        // firebase database
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference("RoI7PGtChhPFjxGQk7WRN4Cs3Mi2");
        retrieveData();
        
        
        // sync data from firebase to thingspeak
        syncButton.setOnClickListener(view -> {
            if (syncJobHandler == null || syncJobHandler.isCancelled()){
                syncJobHandler = scheduler.scheduleAtFixedRate(syncJob,1L, 15L, TimeUnit.SECONDS);
                ((MaterialButton) view).setText("Đồng bộ ThingSpeak: Đang bật");
            } else {
                ((MaterialButton) view).setText("Đồng bộ ThingSpeak: Đang tắt");
                syncJobHandler.cancel(true);
            }

        });
    }


    private void retrieveData() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                relaySnapshot = snapshot.child("Relay");
                sensorSnapshot = snapshot.child("Sensor_Value");
                setValueSnapshot = snapshot.child("Set_Value");

                bindDataToUI(relaySnapshot, sensorSnapshot, setValueSnapshot);

                controlPumpsAsHumidityChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SettingsActivity.this, "Fail to get data.", Toast.LENGTH_SHORT).show();
            }
        });

        // run one time only
        databaseReference.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                relaySnapshot = task.getResult().child("Relay");
                sensorSnapshot = task.getResult().child("Sensor_Value");
                setValueSnapshot = task.getResult().child("Set_Value");

                bindDataToUI(relaySnapshot, sensorSnapshot, setValueSnapshot);
            }
        });
    }

    private void controlPumpsAsHumidityChanged() {
        if (!mode)
            return;
        synchronized (humid1){
            if (humid1.get() < humidThreshold){ // turn the pump on
                updateData(relaySnapshot.getRef().child("relay_2"), "0");
            } else if (humid1.get() > humidThreshold + 5){
                updateData(relaySnapshot.getRef().child("relay_2"), "1");
            }        }
        synchronized (humid2){
            if (humid2.get() < humidThreshold){ // turn the pump on
                updateData(relaySnapshot.getRef().child("relay_3"), "0");
            } else if (humid2.get() > humidThreshold + 5){
                updateData(relaySnapshot.getRef().child("relay_3"), "1");
            }
        }
        synchronized (humid3){
            if (humid3.get() < humidThreshold){ // turn the pump on
                updateData(relaySnapshot.getRef().child("relay_4"), "0");
            } else if (humid3.get() > humidThreshold + 5){
                updateData(relaySnapshot.getRef().child("relay_4"), "1");
            }
        }

    }

    private void bindDataToUI(DataSnapshot relaySnapshot, DataSnapshot sensorSnapshot, DataSnapshot setValueSnapshot) {
        rp = new RequestParams();
        rp.add("api_key", "OIC6SQI33DRM4H08");
        synchronized (humid1){
            humid1.set(sensorSnapshot.child("humi_soil_1").getValue(Long.class));
            progressBar_humid1.setProgress(Math.toIntExact(humid1.get()));
            txt_humid1.setText(humid1.get() + "%");
            rp.add("field4", String.valueOf(humid1.get()));
            System.out.println("HUMID1 UPDATED: " + humid1.get());
        }
        synchronized (humid2){
            humid2.set(sensorSnapshot.child("humi_soil_2").getValue(Long.class));
            progressBar_humid2.setProgress(Math.toIntExact(humid2.get()));
            txt_humid2.setText(humid2.get() + "%");
            rp.add("field5", String.valueOf(humid2.get()));
            System.out.println("HUMID2 UPDATED: " + humid2.get());
        }
        synchronized (humid3){
            humid3.set(sensorSnapshot.child("humi_soil_3").getValue(Long.class));
            progressBar_humid3.setProgress(Math.toIntExact(humid3.get()));
            txt_humid3.setText(humid3.get() + "%");
            rp.add("field6", String.valueOf(humid3.get()));
            System.out.println("HUMID3 UPDATED: " + humid3.get());
        }
        synchronized (temp1){
            temp1.set(sensorSnapshot.child("temp_soil_1").getValue(Long.class));
            txt_temp1.setText(temp1.get() + "°C");
            rp.add("field1", String.valueOf(temp1.get()));
            System.out.println("TEMP1 UPDATED: " + temp1.get());
        }
        synchronized (temp2){
            temp2.set(sensorSnapshot.child("temp_soil_2").getValue(Long.class));
            txt_temp2.setText(temp2.get() + "°C");
            rp.add("field2", String.valueOf(temp2.get()));
            System.out.println("TEMP2 UPDATED: " + temp2.get());
        }
        synchronized (temp3){
            temp3.set(sensorSnapshot.child("temp_soil_3").getValue(Long.class));
            txt_temp3.setText(temp3.get() + "°C");
            rp.add("field3", String.valueOf(temp3.get()));
            System.out.println("TEMP3 UPDATED: " + temp3.get());
        }
        // create sync job
        System.out.println("DATA to sync: "+humid1.get()+" "+humid2.get()+" " + humid3.get());
        syncJob = () -> {
            client.post(thingSpeakPostAPI, rp, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    System.out.println("SYNC TO THINGSPEAK: SUCCESS - " + response.toString());
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                    System.out.println("SYNC TO THINGSPEAK: SUCCESS - " + response.toString());
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                    System.out.println("SYNC TO THINGSPEAK: FAIL - " + statusCode + " - " + throwable.getMessage());
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    System.out.println("SYNC TO THINGSPEAK: FAIL - " + throwable.getMessage());
                }
            });
        };



        humidThreshold = Long.parseLong(setValueSnapshot.child("Soil").getValue(String.class));
        humidThresholdEditText.setText(humidThreshold.toString());

        relay1 = relaySnapshot.child("relay_1").getValue(String.class).equals("0");
        relay2 = relaySnapshot.child("relay_2").getValue(String.class).equals("0");
        relay3 = relaySnapshot.child("relay_3").getValue(String.class).equals("0");
        relay4 = relaySnapshot.child("relay_4").getValue(String.class).equals("0");
        mode = setValueSnapshot.child("Mode").getValue(String.class).equals("1");

        updateDataInSettingsFragment();
    }

    private void updateDataInSettingsFragment() {
        Bundle bundle = new Bundle();
        bundle.putBoolean("relay1", relay1);
        bundle.putBoolean("relay2", relay2);
        bundle.putBoolean("relay3", relay3);
        bundle.putBoolean("relay4", relay4);
        bundle.putBoolean("mode", mode);
        SettingsFragment settingsFragment = new SettingsFragment();
        settingsFragment.setArguments(bundle);
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (!fragmentManager.isDestroyed())
            fragmentManager.beginTransaction()
                    .replace(R.id.settings, settingsFragment)
                    .commit();
        else
            System.out.println("SETTINGSFRAGMENT is destroyed!");
    }
    private void updateData(DatabaseReference field, Object value){
        field.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                field.setValue(value);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SettingsActivity.this, "Fail to update data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        FirebaseDatabase firebaseDatabase;
        DatabaseReference databaseReference;
        // UI
        SwitchPreferenceCompat modeSwitch, pump1, van1, van2, van3;
        PreferenceCategory preferenceCategory;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            modeSwitch = findPreference("mode");
            pump1 = findPreference("pump1");
            van1 = findPreference("van1");
            van2 = findPreference("van2");
            van3 = findPreference("van3");
            preferenceCategory = findPreference("pumpPreference");

            firebaseDatabase = FirebaseDatabase.getInstance();
            databaseReference = firebaseDatabase.getReference("RoI7PGtChhPFjxGQk7WRN4Cs3Mi2");

            loadDataFromActivity();

        }

        private void loadDataFromActivity() {
            Bundle bundle = getArguments();
            if (bundle != null) {
                modeSwitch.setChecked(bundle.getBoolean("mode"));
                if(modeSwitch.isChecked()){
                    preferenceCategory.setEnabled(false);
                }
                modeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isChecked = (boolean) newValue;
                    if (isChecked){
                        preferenceCategory.setEnabled(false);
                        updateData(databaseReference.child("Set_Value").child("Mode"), "1");
                    }
                    else{
                        preferenceCategory.setEnabled(true);
                        updateData(databaseReference.child("Set_Value").child("Mode"), "0");
                    }
                    return true;
                });

                pump1.setChecked(bundle.getBoolean("relay1"));
                pump1.setOnPreferenceChangeListener(((preference, newValue) -> {
                    boolean isChecked = (boolean) newValue;
                    if (isChecked)
                        updateData(databaseReference.child("Relay").child("relay_1"), "0");
                    else
                        updateData(databaseReference.child("Relay").child("relay_1"), "1");
                    return true;
                }));

                van1.setChecked(bundle.getBoolean("relay2"));
                van1.setOnPreferenceChangeListener(((preference, newValue) -> {
                    boolean isChecked = (boolean) newValue;
                    if (isChecked)
                        updateData(databaseReference.child("Relay").child("relay_2"), "0");
                    else
                        updateData(databaseReference.child("Relay").child("relay_2"), "1");
                    return true;
                }));

                van2.setChecked(bundle.getBoolean("relay3"));
                van2.setOnPreferenceChangeListener(((preference, newValue) -> {
                    boolean isChecked = (boolean) newValue;
                    if (isChecked)
                        updateData(databaseReference.child("Relay").child("relay_3"), "0");
                    else
                        updateData(databaseReference.child("Relay").child("relay_3"), "1");
                    return true;
                }));

                van3.setChecked(bundle.getBoolean("relay4"));
                van3.setOnPreferenceChangeListener(((preference, newValue) -> {
                    boolean isChecked = (boolean) newValue;
                    if (isChecked)
                        updateData(databaseReference.child("Relay").child("relay_4"), "0");
                    else
                        updateData(databaseReference.child("Relay").child("relay_4"), "1");
                    return true;
                }));

                if(van1.isChecked() || van2.isChecked() || van3.isChecked()){
                    turnPumpOn();
                } else if(!van1.isChecked() && !van2.isChecked() && !van3.isChecked()) {
                    turnPumpOff();
                }
            }

        }

        private void turnPumpOn() {
            pump1.setChecked(true);
            updateData(databaseReference.child("Relay").child("relay_1"), "0");
        }

        private void turnPumpOff() {
            pump1.setChecked(false);
            updateData(databaseReference.child("Relay").child("relay_1"), "1");
        }
        private void updateData(DatabaseReference field, Object value){
            field.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    field.setValue(value);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(getContext(), "Fail to update data.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}