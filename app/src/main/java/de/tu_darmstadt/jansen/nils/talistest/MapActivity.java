package de.tu_darmstadt.jansen.nils.talistest;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        //important! set your user agent to prevent getting banned from the osm servers
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        setContentView(R.layout.activity_map);

        MapView map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        List<GeoPoint> geoPoints = getGeoPoints();
        IMapController mapController = map.getController();
        mapController.setZoom(20);
        GeoPoint startPoint = new GeoPoint(geoPoints.get(0).getLatitude(), geoPoints.get(0).getLongitude());
        mapController.setCenter(startPoint);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        Polyline line = new Polyline();
        line.setPoints(geoPoints);
        map.getOverlayManager().add(line);
    }

    /**
     * Reads geopoints from the .gpx location file into a list
     *
     * @return
     */
    private List<GeoPoint> getGeoPoints() {
        List<GeoPoint> geoPoints = new ArrayList<>();
        Uri uri = getIntent().getParcelableExtra(MainActivity.EXTRA_COORDS);
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        GeoPoint geoPoint;
        String lat = "";
        String lon = "";
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.contains("<trkpt")) {
                    line.indexOf("\"", 0);
                    Integer index = line.indexOf("\"", 0) + 1;
                    lat = line.substring(index, line.indexOf("\"", index));
                    index = line.indexOf("\"", line.indexOf("\"", index) + 1) + 1;
                    lon = line.substring(index, line.indexOf("\"", index));
                    geoPoint = new GeoPoint(Float.parseFloat(lat), Float.parseFloat(lon));
                    geoPoints.add(geoPoint);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return geoPoints;
    }

    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
    }
}
