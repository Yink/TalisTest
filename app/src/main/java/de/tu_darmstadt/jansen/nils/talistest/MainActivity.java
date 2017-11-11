package de.tu_darmstadt.jansen.nils.talistest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {
    private final String LOG_TAG = "Talistest";
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private static final int REQUEST_AUDIO_LOCATION_PERMISSIONS = 200;
    private boolean permissionToRecordAccepted = false;
    private final String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    private File filePath;
    private String fileName;
    private boolean connected = false;
    private Button buttonRecord;
    private AudioManager aManager;
    private boolean wantToRecord = false;
    private GoogleApiClient googleApiClient;
    private long locationInterval = 30000;
    private LocationRequest locationRequest;
    private Location currentLocation;
    private String currentTime;
    private DateFormat dateFormat;
    private final String datePattern = "yyyy-mm-dd-kk-mm-ss";

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_AUDIO_LOCATION_PERMISSIONS:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();

    }

    private BroadcastReceiver bluetoothScoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED & wantToRecord) {
                Log.d(LOG_TAG, "CONNECTION SUCCESS");
                connected = true;
                isRecording = true;
                startRecording();
                buttonRecord.setText("Stop recording");
            }
        }
    };


    private void startRecording() {
        if (isExternalStorageWritable()) {
            filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + "talistest");
            filePath.mkdirs();
            Log.e(LOG_TAG, filePath.getAbsolutePath());
        } else {
            Toast.makeText(getApplicationContext(), "External storage not writable", Toast.LENGTH_LONG).show();
            return;
        }

        Date date = Calendar.getInstance().getTime();
        fileName = dateFormat.format(date);
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        File audioFile = new File(filePath, fileName + ".3gp");

        if (!audioFile.exists()) {
            try {
                audioFile.createNewFile();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
        mRecorder.setOutputFile(audioFile.getAbsolutePath());
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
            startLocationUpdates();
            mRecorder.start();
            Log.e(LOG_TAG, audioFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(LOG_TAG, "MediaRecorder.prepare() failed: " + e.getMessage());
            Toast.makeText(getApplicationContext(), "MediaRecorder.prepare() failed", Toast.LENGTH_LONG).show();
        }

    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        stopLocationUpdates();
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(filePath.getAbsolutePath() + "/" + fileName + ".3gp");
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "MediaPlayer.prepare() failed");
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }

    protected void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(locationInterval);
        locationRequest.setFastestInterval(locationInterval);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @SuppressLint("MissingPermission")
    protected void startLocationUpdates() {
        //permission is checked by function above
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        //permission is checked by function above
        currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (currentLocation != null) {
            ((TextView) findViewById(R.id.textView_coordinates)).setText(currentLocation.toString());
        } else {
            Toast.makeText(getApplicationContext(), "Location = null", Toast.LENGTH_LONG).show();
        }
    }


    public boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    //Default methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_AUDIO_LOCATION_PERMISSIONS);

        aManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        createLocationRequest();

        dateFormat = new SimpleDateFormat(datePattern);

        buttonRecord = findViewById(R.id.button_record);
        buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    if (connected) {
                        isRecording = true;
                        startRecording();
                        buttonRecord.setText("Stop recording");
                    } else {
                        wantToRecord = true;
                        aManager.startBluetoothSco();
                        Toast.makeText(getApplicationContext(), "Starting Bluetooth connection", Toast.LENGTH_LONG).show();
                    }
                } else {
                    wantToRecord = false;
                    isRecording = false;
                    stopRecording();
                    buttonRecord.setText("Start recording");
                }
            }
        });
        final Button buttonPlay = findViewById(R.id.button_play);
        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlaying) {
                    /*aManager.setMode(AudioManager.MODE_NORMAL);
                    aManager.setSpeakerphoneOn(true);*/
                    isPlaying = true;
                    startPlaying();
                    buttonPlay.setText("Stop playing");
                } else {
                    isPlaying = false;
                    stopPlaying();
                    buttonPlay.setText("Start playing");
                }
            }
        });

        findViewById(R.id.textView_coordinates).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLocation();
            }
        });

    }

    @Override
    public void onStop() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        connected = false;
        isPlaying = false;
        isRecording = false;
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onResume() {

        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        Intent intent = registerReceiver(bluetoothScoReceiver, intentFilter);
        if (intent == null) {
            Log.e(LOG_TAG, "Failed to register bluetooth sco receiver...");
            return;
        }

        int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
            connected = true;
        }
        super.onResume();
        // Ensure the SCO audio connection stays active in case the
        // current initiator stops it.
        /*AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.startBluetoothSco();*/
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(bluetoothScoReceiver);
        connected = false;
        if (aManager != null)
            aManager.stopBluetoothSco();
    }

    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /**
     * Called when the location has changed.
     * <p>
     * <p> There are no restrictions on the use of the supplied Location object.
     *
     * @param location The new location, as a Location object.
     */
    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        Date date = Calendar.getInstance().getTime();
        currentTime = dateFormat.format(date);
        File locationFile = new File(filePath, fileName + ".txt");
        FileOutputStream outputStream = null;
        if (!locationFile.exists()) {
            try {
                locationFile.createNewFile();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
        try {
            try {
                outputStream = new FileOutputStream(locationFile);
                outputStream.write((currentTime + " " + location.getAltitude() + " " + location.getLongitude()).getBytes());
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

}
