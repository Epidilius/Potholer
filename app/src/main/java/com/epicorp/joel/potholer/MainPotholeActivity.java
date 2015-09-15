package com.epicorp.joel.potholer;

/**
 * Created by Joel on 9/15/2015.
 */


import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.twotoasters.clusterkraf.Clusterkraf;
import com.twotoasters.clusterkraf.InputPoint;
import com.twotoasters.clusterkraf.Options.ClusterClickBehavior;
import com.twotoasters.clusterkraf.Options.ClusterInfoWindowClickBehavior;
import com.twotoasters.clusterkraf.Options.SinglePointClickBehavior;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
//TODO: Add activity recognition. Then, when the user is walking, stop recording, switch to manual records layout
//When the user is driving, turn on recording, switch to map layout
//When the user is cycling, switch to map layout, and switch car type to "bicycle"

//TODO: Add three options for potholes: mild (which has blue markers), noticeable (which has yellow markers), and severe (which has red markers)

//TODO: Add focus and unfocus (through panning)
public class MainPotholeActivity extends Activity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        NumberPicker.OnValueChangeListener, LocationListener {
    // Google Map
    private GoogleMap googleMap;
    Location location;
    private ToggleButton btnDriving;
    private final String PREFERENCE_FILE_KEY = "POTHOLE_APP_PREF_FILE_KEY";
    private final String PREFERENCE_CAR_TYPE = "POTHOLE_APP_PREF_CAR_TYPE";
    private final String PREFERENCE_SEVERITY_CHOICE = "POTHOLE_APP_PREF_SEVERITY_CHOICE";
    protected static final String PREFS_FILE = "device_id.xml";
    protected static final String PREFS_DEVICE_ID = "device_id";
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    private String carType;
    private int potholeSeverityChoice;
    private String TAG = this.getClass().getSimpleName();
    private LocationClient locationclient;
    private LocationRequest locationrequest;
    JSONParser jParser = new JSONParser();
    CopyOnWriteArrayList<HashMap<String, String>> potholesList;
    ArrayList<HashMap<String, String>> oldPotholesList;
    float zoomLevel;
    // HashMap<String, AccelData> markerList;
    // url to get all potholes list
    private static String url_all_potholes = "http://tokenfyt.com/PHPScripts/getPothole.php";
    // JSON Node names
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_POTHOLES = "potholes";
    private static final String TAG_PID = "_id";
    private static final String TAG_GPS_X = "gps_x";
    private static final String TAG_GPS_Y = "gps_y";
    private static final String TAG_GPS_X_MIN = "gps_x_min";
    private static final String TAG_GPS_Y_MIN = "gps_y_min";
    private static final String TAG_GPS_X_MAX = "gps_x_max";
    private static final String TAG_GPS_Y_MAX = "gps_y_max";
    private static final String TAG_SEVERITY = "severity";
    private boolean markingInProgress = false;
    LatLng northEast;
    LatLng southWest;
    // ImageView grayBackground;
    NumberPicker potholeSeverityPicker;
    Button potholeOKButton;
    // potholes JSONArray
    JSONArray potholes = null;
    AccelData[] yourMapPointModels;
    ArrayList<InputPoint> inputPoints;
    Clusterkraf clusterkraf;
    com.twotoasters.clusterkraf.Options options;
    ToastedMarkerOptionsChooser markerOptionsChooser;

    // Declare a variable for the cluster manager.

    private Boolean doIWantToCenter = false; //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_potholer_main);

        yourMapPointModels = new AccelData[] {};
        options = new com.twotoasters.clusterkraf.Options(); // TODO: Change
        // these around

        zoomLevel = 17.5F;
        potholesList = new CopyOnWriteArrayList<HashMap<String, String>>();
        // markerList = new HashMap<String, AccelData>();
        inputPoints = new ArrayList<InputPoint>();
        clusterkraf = null;

        markerOptionsChooser = null;

        Context context = getBaseContext();

        sharedPref = context.getSharedPreferences(PREFERENCE_FILE_KEY,
                Context.MODE_PRIVATE);// TODO: Get the R.string.pref_file_key
        // working
        editor = sharedPref.edit();

        carType = sharedPref.getString(PREFERENCE_CAR_TYPE, "sedan");
        potholeSeverityChoice = sharedPref
                .getInt(PREFERENCE_SEVERITY_CHOICE, 7);

        potholeOKButton = (Button) findViewById(R.id.potholeSeverityOK);
        potholeSeverityPicker = (NumberPicker) findViewById(R.id.potholeSeverityNumberPicker);
        // grayBackground = (ImageView) findViewById(R.id.grayBackground);

        potholeOKButton.setVisibility(View.GONE);
        potholeSeverityPicker.setVisibility(View.GONE);
        // grayBackground.setVisibility(View.GONE);

        potholeSeverityPicker.setMaxValue(30);
        potholeSeverityPicker.setMinValue(1);
        potholeSeverityPicker.setWrapSelectorWheel(false);
        potholeSeverityPicker.setOnValueChangedListener(this);
        potholeSeverityPicker.setValue(potholeSeverityChoice);

        btnDriving = (ToggleButton) findViewById(R.id.togglebutton);
        btnDriving.setChecked(false);

        try {
            // Loading map
            initilizeMap();
            // TODO: Get this to work
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
                    location.getLatitude(), location.getLongitude()), 13));
        } catch (Exception e) {
            e.printStackTrace();
        }

        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resp == ConnectionResult.SUCCESS) {
            locationclient = new LocationClient(this, this, this);
            locationclient.connect();
        } else {
            Toast.makeText(this, "Google Play Service Error " + resp,
                    Toast.LENGTH_LONG).show();
        }

        if (!hasRunChecker()) {
            DeviceUUIDFactory uuid = new DeviceUUIDFactory(context);
            // uuid.getDeviceUuid().toString();
        }

        if (isMyServiceRunning()) {
            btnDriving.setChecked(true);
        }
    }

    private boolean hasRunChecker() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_FILE, 0);
        final String id = prefs.getString(PREFS_DEVICE_ID, null);
        //String id = null;

        if (id != null) {
            Log.d(TAG, "UUID check true");
            return true;
        }
        Log.d(TAG, "UUID check false");
        return false;
    }

    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager
                .getRunningServices(Integer.MAX_VALUE)) {
            if (PotholeBackgroundService.class.getName().equals(
                    service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void setUpClusterer() {
        // Position the map.
        // googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new
        // LatLng(51.503186, -0.126446), 10));

        // Initialize the manager with the context and the map.
        // (Activity extends context, so we can pass 'this' in the constructor.)

        // Point the map's listeners at the listeners implemented by the cluster
        // manager.

        // TODO: Fix this

		/*
		 * googleMap.setOnCameraChangeListener(new
		 * ClusterManager<MyClusterItem>( this, googleMap) {
		 *
		 * @Override public void onCameraChange(CameraPosition position) {
		 * LatLngBounds bounds = googleMap.getProjection()
		 * .getVisibleRegion().latLngBounds; northEast = bounds.northeast;
		 * southWest = bounds.southwest; new LoadAllPotholes().execute();
		 *
		 * super.onCameraChange(position); } });
		 */
        // TODO: googleMap.setOnMarkerClickListener(mClusterManager);
    }

    /**
     * function to load map. If map is not created it will create it for you
     * */
    private void initilizeMap() {
        if (googleMap == null) {
            // ClusterManager<Location> mClusterManager;
            googleMap = ((MapFragment) getFragmentManager().findFragmentById(
                    R.id.map)).getMap();
            // check if map is created successfully or not
            if (googleMap == null) {
                Toast.makeText(getApplicationContext(),
                        "Sorry! unable to create maps", Toast.LENGTH_SHORT)
                        .show();
            } else {
                googleMap.setMyLocationEnabled(true);
                googleMap.getUiSettings().setCompassEnabled(false);
                googleMap.getUiSettings().setZoomControlsEnabled(false);

                location = googleMap.getMyLocation();
                // googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                // new LatLng(location.getLatitude(), location
                // .getLongitude()), 15));

                setUpClusterer();
                if (doIWantToCenter) {
                    googleMap
                            .setOnMyLocationButtonClickListener(new OnMyLocationButtonClickListener() {

                                @Override
                                public boolean onMyLocationButtonClick() {
                                    googleMap.animateCamera(CameraUpdateFactory
                                            .newCameraPosition(new CameraPosition(
                                                    new LatLng(
                                                            location.getLatitude(),
                                                            location.getLongitude()),
                                                    zoomLevel,
                                                    googleMap
                                                            .getCameraPosition().tilt,
                                                    googleMap
                                                            .getCameraPosition().bearing)));
                                    doIWantToCenter = true;
                                    return false;
                                }
                            });
                }
                // @Override public boolean onTouch(View v, MotionEvent event) {
                // doIWantToCenter = false; return false; }
            }
        }
    }

    public void onToggleClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            this.startService(new Intent(this, PotholeBackgroundService.class));
        } else {
            this.stopService(new Intent(this, PotholeBackgroundService.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (carType.equals("coupe")) {
            menu.findItem(R.id.coupe).setChecked(true);
            return true;
        } else if (carType.equals("minivan")) {
            menu.findItem(R.id.miniVan).setChecked(true);
            return true;
        } else if (carType.equals("sedan")) {
            menu.findItem(R.id.sedan).setChecked(true);
            return true;
        } else if (carType.equals("truck")) {
            menu.findItem(R.id.truck).setChecked(true);
            return true;
        } else if (carType.equals("van")) {
            menu.findItem(R.id.van).setChecked(true);
            return true;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.potholeSeverity:
                potholeSeverityPicker.setVisibility(View.VISIBLE);
                potholeOKButton.setVisibility(View.VISIBLE);
                // grayBackground.setVisibility(View.VISIBLE);
                return true;
            case R.id.helpMenu:
                return true;
            case R.id.coupe:
                editor.putString(PREFERENCE_CAR_TYPE, "coupe");
                editor.commit();
                return true;
            case R.id.miniVan:
                editor.putString(PREFERENCE_CAR_TYPE, "minivan");
                editor.commit();
                return true;
            case R.id.sedan:
                editor.putString(PREFERENCE_CAR_TYPE, "sedan");
                editor.commit();
                return true;
            case R.id.truck:
                editor.putString(PREFERENCE_CAR_TYPE, "truck");
                editor.commit();
                return true;
            case R.id.van:
                editor.putString(PREFERENCE_CAR_TYPE, "van");
                editor.commit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void hideViews(View v) {
        potholeOKButton.setVisibility(View.GONE);
        potholeSeverityPicker.setVisibility(View.GONE);
        // grayBackground.setVisibility(View.GONE);

        // googleMap.clear();

        LatLngBounds bounds = googleMap.getProjection().getVisibleRegion().latLngBounds;
        northEast = bounds.northeast;
        southWest = bounds.southwest;
        new LoadAllPotholes().execute();
    }

    private void startLocUpdates() {
        locationrequest = LocationRequest.create();
        locationrequest.setInterval(0L);
        locationclient.requestLocationUpdates(locationrequest, this);
    }

    @Override
    public void onLocationChanged(Location locationArgs) {
        if (locationArgs != null) {
            location = locationArgs;
            zoomLevel = googleMap.getCameraPosition().zoom;
            Log.i(TAG, "Location Request :" + location.getLatitude() + ","
                    + location.getLongitude() + "," + zoomLevel);

            if (markerOptionsChooser != null)
                markerOptionsChooser.setZoomLevel(zoomLevel);

            if (doIWantToCenter == false)
                return;
            LatLng myLocation = new LatLng(location.getLatitude(),
                    location.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    myLocation, zoomLevel));
        }
    }

    private void stopLocUpdates() {
        locationclient.removeLocationUpdates(this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        Log.i(TAG, "onConnectionFailed");
        stopLocUpdates();
    }

    @Override
    public void onConnected(Bundle arg0) {
        Log.i(TAG, "onConnected");
        startLocUpdates();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "onDisconnected");
    }

    /**
     * Background Async Task to Load all pothole by making HTTP Request
     * */
    class LoadAllPotholes extends AsyncTask<String, String, String> {
        // TODO: Put a "Downloading potholes notification?"
        /**
         * Before starting background thread Show Progress Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.v(TAG, "onPreExecute");
        }

        /**
         * getting All potholes from url
         * */
        protected String doInBackground(String... args) {
            // Building Parameters
            Log.v(TAG, "doInBackground");
            // ArrayList<InputPoint> inputPoints;
			/*
			 * if (markingInProgress = false) { markingInProgress = true; for
			 * (InputPoint entry : inputPoints) { // TODO: Change to go through
			 * inputPoints int key = inputPoints.indexOf(entry); AccelData value
			 * = (AccelData) entry.getTag(); if (value.getLat() <
			 * southWest.latitude || value.getLat() > northEast.latitude ||
			 * value.getLng() < southWest.longitude || value.getLng() >
			 * northEast.longitude || value.getSeverity() <
			 * potholeSeverityChoice) { Log.d(TAG, "Removed point" +
			 * entry.toString()); inputPoints.remove(key); } } markingInProgress
			 * = false; }
			 */

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair(TAG_GPS_X_MIN, Double
                    .toString(southWest.latitude)));
            params.add(new BasicNameValuePair(TAG_GPS_X_MAX, Double
                    .toString(northEast.latitude)));
            params.add(new BasicNameValuePair(TAG_GPS_Y_MIN, Double
                    .toString(southWest.longitude)));
            params.add(new BasicNameValuePair(TAG_GPS_Y_MAX, Double
                    .toString(northEast.longitude)));
            params.add(new BasicNameValuePair(TAG_SEVERITY, Integer
                    .toString(potholeSeverityChoice)));

            // params.add(new BasicNameValuePair(TAG_TIME, Long
            // .toString(sensorData.get(i).getTimestamp())));
            // TODO: Add parameters here, also, find a way to only new info.
            // Maybe, save the old NE and SW, then pass them along as min or
            // max, depending on if its more or less than the new
            // getting JSON string from URL
            // TODO: Add a check to if this is already running before starting
            // another one
            JSONObject json = jParser.makeHttpRequest(url_all_potholes, "GET",
                    params);

            // Check your log cat for JSON reponse
            if (json != null) {
                Log.d("All Potholes: ", json.toString());

                try {
                    // Checking for SUCCESS TAG
                    int success = json.getInt(TAG_SUCCESS);

                    if (success == 1) {
                        // potholes found
                        // Getting Array of Potholes
                        potholes = json.getJSONArray(TAG_POTHOLES);
                        boolean idExists;
                        // looping through All Potholes
                        for (int i = 0; i < potholes.length(); i++) {
                            idExists = false;
                            JSONObject c = potholes.getJSONObject(i);
                            // Storing each json item in variable
                            String id = c.getString(TAG_PID);
                            String gps_x = c.getString(TAG_GPS_X);
                            String gps_y = c.getString(TAG_GPS_Y);
                            String severity = c.getString(TAG_SEVERITY);

                            // creating new HashMap
                            HashMap<String, String> map = new HashMap<String, String>();

                            for (HashMap<String, String> entry : potholesList) {
                                if (entry.get(TAG_PID).equals(id)) {
                                    idExists = true;
                                    Log.d(TAG, "ID " + entry.get(TAG_PID)
                                            + " already exists");
                                    break;
                                }
                            }
                            if (idExists)
                                continue;
                            // adding each child node to HashMap key => value
                            map.put(TAG_PID, id);
                            map.put(TAG_GPS_X, gps_x);
                            map.put(TAG_GPS_Y, gps_y);
                            map.put(TAG_SEVERITY, severity);

                            // adding HashList to ArrayList
                            potholesList.add(map);
                        }
                    } else {
                        // no potholes found
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        /**
         * After completing background task Dismiss the progress dialog
         * **/
        protected void onPostExecute(String file_url) {
            // updating UI from Background Thread
            runOnUiThread(new Runnable() {
                public void run() {
                    /**
                     * Updating parsed JSON data into ListView
                     * */
                    // TODO: I could try the multi database setup, it will make
                    // things
                    // somewhat easier. There has to be an easy way to search
                    // through a
                    // single database field

                    // TODO: Make a small red dot, use it as my marker. Do the
                    // ground
                    // overlay one, I think

                    for (HashMap<String, String> map : potholesList) {
                        if (map.equals(null)) {
                            continue;
                        } else {
							/*
							 * Marker markerOld = googleMap .addMarker(new
							 * MarkerOptions().position( markerPosition).alpha(
							 * (0.01f * (Float.parseFloat(map
							 * .get(TAG_SEVERITY))))));
							 */

                            AccelData marker = new AccelData(
                                    Double.parseDouble(map.get(TAG_SEVERITY)),
                                    Double.parseDouble(map.get(TAG_GPS_X)),
                                    Double.parseDouble(map.get(TAG_GPS_Y)),
                                    Long.parseLong(map.get(TAG_PID)));

                            // mClusterManager.addItem(marker);
                            LatLng tempLatLng = new LatLng(
                                    Double.parseDouble(map.get(TAG_GPS_X)),
                                    Double.parseDouble(map.get(TAG_GPS_Y)));
                            // markerList.put(map.get(TAG_PID), marker);
                            inputPoints.add(new InputPoint(tempLatLng, marker));

                            // googleMap.addMarker(New
                            // MarkerOptions().position(new
                            // LatLng(map.get(TAG_GPS_X), map.get(TAG_GPS_Y))));
                            // TODO: Store locally, and add new potholes on
                            // refresh? For quicker browsing?
                        }
                    }
                    updateClusterkraf();
                    Log.d(TAG, "Marking complete");
                }
            });
        }
    }

    private void updateClusterkraf() {
		/*
		 * Clusterkraf calculates whether InputPoint objects should join a
		 * cluster based on their pixel proximity. If you want to offer your app
		 * on devices with different screen densities, you should identify a
		 * Device Independent Pixel measurement and convert it to pixels based
		 * on the device's screen density at runtime.
		 */
        if (this.googleMap != null && this.inputPoints != null
                && this.inputPoints.size() > 0) {
            // customize the options before you construct a Clusterkraf instance
            if (clusterkraf == null) {
                this.options
                        .setSinglePointClickBehavior(SinglePointClickBehavior.SHOW_INFO_WINDOW);
                this.options
                        .setClusterClickBehavior(ClusterClickBehavior.SHOW_INFO_WINDOW);
                this.options
                        .setClusterInfoWindowClickBehavior(ClusterInfoWindowClickBehavior.HIDE_INFO_WINDOW);
                this.options.setPixelDistanceToJoinCluster(1);

                markerOptionsChooser = new ToastedMarkerOptionsChooser(this,
                        inputPoints.get(0));
                options.setMarkerOptionsChooser(markerOptionsChooser);

                this.clusterkraf = new Clusterkraf(this.googleMap,
                        this.options, this.inputPoints);

            } else {
                clusterkraf.clear(); // TODO: Does this clear the map? Try to
                // find a way to only remove the markers
                // I want to
                clusterkraf.addAll(inputPoints);
            }
        }
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        potholeSeverityChoice = newVal;
        Log.d(TAG, Integer.toString(newVal));
        Log.d(TAG, Integer.toString(picker.getValue()));
        editor.putInt(PREFERENCE_SEVERITY_CHOICE, potholeSeverityChoice);
        editor.commit();
    }
}