package de.tu_darmstadt.jansen.nils.talistest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {
    public static final String EXTRA_COORDS = "de.tu_darmstadt.jansen.nils.talistest.COORDS";
    private static final int REQUEST_AUDIO_LOCATION_PERMISSIONS = 200;
    private static final int RC_READ_FILE = 42;
    private static final int RC_SIGN_IN = 69;
    private final String LOG_TAG = "Talistest";
    private final String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    private final String datePattern = "yyyy-MM-dd-kk-mm-ss";
    private File audioFile;
    private File locationFile;
    private boolean toggle_drive = false;
    private boolean toggle_bluetooth = true;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private boolean permissionToRecordAccepted = false;
    private File filePath;
    private String fileName;
    private boolean connected = false;
    private Button buttonRecord;
    private AudioManager aManager;
    private boolean wantToRecord = false;
    private GoogleApiClient googleApiClient;
    private long locationInterval = 10000;
    private LocationRequest locationRequest;
    private Location currentLocation;
    private String currentTime;
    private DateFormat dateFormat;
    private GoogleSignInClient googleSignInClient;
    private DriveClient driveClient;
    private DriveResourceClient driveResourceClient;
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

    /**
     * Prepares and starts mediarecorder, prepares audiofile in storage
     */
    private void startRecording() {
        if (isExternalStorageWritable()) {
            filePath = new File(Environment.getExternalStorageDirectory(), "talistest");
        } else {
            showMessage("External storage not writable");
            return;
        }

        Date date = Calendar.getInstance().getTime();
        fileName = dateFormat.format(date);
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        audioFile = new File(filePath, fileName + ".3gp");

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
            Log.d(LOG_TAG, audioFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(LOG_TAG, "MediaRecorder.prepare() failed: " + e.getMessage());
            showMessage("MediaRecorder.prepare() failed");
        }

    }

    /**
     * Stops mediarecorder, saves audio and location files
     */
    private void stopRecording() {
        mRecorder.stop();
        MediaScannerConnection.scanFile(this, new String[]{audioFile.getAbsolutePath(), locationFile.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.d(LOG_TAG, path + " successfully scanned");
            }
        });
        mRecorder.release();
        mRecorder = null;
        stopLocationUpdates();
        OutputStream outputStream = null;
        try {
            try {
                outputStream = new FileOutputStream(locationFile, true);
                outputStream.write(("</trkseg></trk>\n" +
                        "</gpx>").getBytes());
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage());
        }
        if (toggle_drive) {
            saveFileToDrive(audioFile);
            saveFileToDrive(locationFile);
        }
    }

    /**
     * Plays the most recent audio file
     */
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

    /**
     * Stops the currently playing audio file
     */
    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }

    /**
     * Requests current location
     */
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


    public boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Opens document searching dialogue and limits the options to images
     */
    private void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, RC_READ_FILE);
    }

    /**
     * Checks whether Location service is enabled
     */
    public boolean isLocationServiceEnabled() {
        LocationManager locationManager = locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        boolean location_enabled = false;

        try {
            location_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

        return location_enabled;
    }

    /**
     * Initializes ui, signs into google account and connects to google api
     *
     * @param savedInstanceState
     */
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
                    if (connected || !toggle_bluetooth) {
                        if (isLocationServiceEnabled()) {
                            isRecording = true;
                            startRecording();
                            buttonRecord.setText("Stop recording");
                        } else {
                            showMessage("Please Enable Location Service");
                        }
                    } else {
                        wantToRecord = true;
                        aManager.startBluetoothSco();
                        showMessage("Trying to start bluetooth connection");
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

        ToggleButton toggleButton_drive = findViewById(R.id.toggleButton);
        toggleButton_drive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                toggle_drive = isChecked;
            }
        });

        ToggleButton toggleButton_bluetooth = findViewById(R.id.toggleButton2);
        toggleButton_bluetooth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                toggle_bluetooth = isChecked;
            }
        });
        toggleButton_bluetooth.setChecked(true);

        final Button buttonMap = findViewById(R.id.button_map);
        buttonMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performFileSearch();
            }
        });

        googleSignInClient = buildGoogleSignInClient();
        startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
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

    /**
     * Ensure the SCO audio connection stays active in case the
     * current initiator stops it.
     */
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
     * saves current location as a line in the .gpx location file
     * initializes file if not existent
     *
     * @param location The new location, as a Location object.
     */
    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        Date date = Calendar.getInstance().getTime();
        currentTime = dateFormat.format(date);
        locationFile = new File(filePath, fileName + ".gpx");
        FileOutputStream outputStream = null;
        if (!locationFile.exists()) {
            try {
                locationFile.createNewFile();
                outputStream = new FileOutputStream(locationFile, false);
                outputStream.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<gpx version=\"1.0\">\n" +
                        "\t<trk><name>Route from " + fileName + "</name><number>1</number><trkseg>\n").getBytes());
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
        try {
            try {
                outputStream = new FileOutputStream(locationFile, true);
                outputStream.write(("<trkpt lat=\"" + location.getLatitude() + "\" lon=\"" + location.getLongitude() + "\"><time>" + currentTime + "</time></trkpt>\n").getBytes());
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }


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

    /**
     * Reacts to intents, either by opening the map or initializing google drive
     *
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == RC_READ_FILE && resultCode == Activity.RESULT_OK) {
            if (intent != null) {
                Intent intentMap = new Intent(this, MapActivity.class);
                intentMap.putExtra(EXTRA_COORDS, intent.getData());
                startActivity(intentMap);
            }
        } else if (requestCode == RC_SIGN_IN && resultCode == Activity.RESULT_OK) {
            Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(intent);
            if (getAccountTask.isSuccessful()) {
                initializeDriveClient(getAccountTask.getResult());
            }
        }
    }

    private void initializeDriveClient(GoogleSignInAccount signInAccount) {
        driveClient = Drive.getDriveClient(getApplicationContext(), signInAccount);
        driveResourceClient = Drive.getDriveResourceClient(getApplicationContext(), signInAccount);
        showMessage("Signed into Google Account");
    }


    /**
     * Saves the file to Google Drive and sets the MIME type
     * @param file
     */
    private void saveFileToDrive(final File file) {
        final Task<DriveFolder> appFolderTask = driveResourceClient.getRootFolder();
        final Task<DriveContents> createContentsTask = driveResourceClient.createContents();
        Tasks.whenAll(appFolderTask, createContentsTask)
                .continueWithTask(new Continuation<Void, Task<DriveFile>>() {
                    @Override
                    public Task<DriveFile> then(@NonNull Task<Void> task) throws Exception {
                        DriveFolder parent = appFolderTask.getResult();
                        DriveContents contents = createContentsTask.getResult();
                        byte[] b = new byte[(int) file.length()];
                        FileInputStream fileInputStream = new FileInputStream(file);
                        fileInputStream.read(b);
                        OutputStream outputStream = contents.getOutputStream();
                        outputStream.write(b);
                        String mimeType;
                        if (file.getName().contains(".gpx"))
                            mimeType = "text/plain";
                        else
                            mimeType = "audio/3gp";
                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                .setTitle(file.getName())
                                .setMimeType(mimeType)
                                .setStarred(true)
                                .build();

                        return driveResourceClient.createFile(parent, changeSet, contents);
                    }
                })
                .addOnSuccessListener(this,
                        new OnSuccessListener<DriveFile>() {
                            @Override
                            public void onSuccess(DriveFile driveFile) {
                                showMessage("File uploaded to Google Drive successfully");
                            }
                        })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(LOG_TAG, "Unable to create file", e);
                    }
                });
    }

    protected void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
