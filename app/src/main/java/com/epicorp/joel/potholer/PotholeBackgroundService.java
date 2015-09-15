package com.epicorp.joel.potholer;

/**
 * Created by Joel on 9/15/2015.
 */


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import org.apache.http.NameValuePair;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PotholeBackgroundService extends Service implements
        SensorEventListener, GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener, LocationListener {
    private static Context CONTEXT;
    private Long oldTimestamp;
    private ArrayList<AccelData> sensorData;
    private ArrayList<AccelData> sensorDataOld;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private String provider;
    private Location location;

    private double velocity;

    private NotificationManager mNM;
    private int NOTIFICATION = 10001; // Any unique number for this notification
    private int NOTIFICATION_UPLOAD = 10002;
    private int NOTIFICATION_TESTING = 10003;
    private int NOTIFICATION_SPEED = 10004;

    private static final String url_update_pothole = "http://tokenfyt.com/PHPScripts/newPothole.php";
    private static final String url_update_client = "http://tokenfyt.com/PHPScripts/updateClient.php";
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_NUMBER_OF_POTHOLES = "numberOfPotholes";
    private static final String TAG_USERNAME = "username";
    private static final String TAG_TIME = "time";
    private static final String TAG_ACCEL_X = "accel_x";
    private static final String TAG_ACCEL_Y = "accel_y";
    private static final String TAG_ACCEL_Z = "accel_z";
    private static final String TAG_CLIENT_ID = "client_id";
    private static final String TAG_CAR_TYPE = "car_type";
    private static final String TAG_GPS_X = "gps_x";
    private static final String TAG_GPS_Y = "gps_y";
    private String username = "Anonymous";
    private String carType;

    private Location oldLoc;

    private String TAG = this.getClass().getSimpleName();
    private LocationClient locationclient;
    private LocationRequest locationrequest;
    private Boolean servicesAvailable = false;
    private Boolean inProgress;
    JSONParser jsonParser = new JSONParser();

    int numberOfPotholes = -1;

    SharedPreferences sharedPref;
    private final String PREFERENCE_FILE_KEY = "POTHOLE_APP_PREF_FILE_KEY";
    private final String PREFERENCE_CAR_TYPE = "POTHOLE_APP_PREF_CAR_TYPE";
    protected static final String PREFS_FILE = "device_id.xml";
    protected static final String PREFS_DEVICE_ID_NO_DASHES = "device_id_no_dashes";
    SharedPreferences prefs;
    String id;

    NotificationCompat.Builder mNotifyBuilder;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Put your code here
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("Started listening");
        CONTEXT = this.getApplicationContext();
        sensorData = new ArrayList<AccelData>();
        sensorDataOld = new ArrayList<AccelData>();

        prefs = getSharedPreferences(PREFS_FILE, 0);
        id = prefs.getString(PREFS_DEVICE_ID_NO_DASHES, null);

        Log.d(TAG, "ID: " + id);

        sharedPref = CONTEXT.getSharedPreferences(PREFERENCE_FILE_KEY,
                Context.MODE_PRIVATE);
        carType = sharedPref.getString(PREFERENCE_CAR_TYPE, "sedan");

        oldTimestamp = 0L;

        inProgress = false;
        servicesAvailable = servicesConnected();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Toast.makeText(getApplicationContext(), "Started recording service",
                Toast.LENGTH_SHORT).show();

        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        location = locationManager.getLastKnownLocation(provider);

        Sensor accel = sensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accel,
                SensorManager.SENSOR_DELAY_FASTEST);

        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resp == ConnectionResult.SUCCESS) {
            locationclient = new LocationClient(this, this, this);
            locationclient.connect();

            location = locationManager.getLastKnownLocation(provider);
            oldLoc = locationManager.getLastKnownLocation(provider);
        } else {
            Toast.makeText(this, "Google Play Service Error " + resp,
                    Toast.LENGTH_LONG).show();
        }

        showNotification();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!servicesAvailable || locationclient.isConnected() || inProgress)
            return START_STICKY;

        setUpLocationClientIfNeeded();
        if (!locationclient.isConnected() || !locationclient.isConnecting()
                && !inProgress) {
            locationclient.connect();
            inProgress = true;
        }

        return START_STICKY;
    }

    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            return true;
        } else {
            return false;
        }
    }

    private void setUpLocationClientIfNeeded() {
        if (locationclient == null)
            locationclient = new LocationClient(this, this, this);
    }

    @Override
    public void onDestroy() {
        uploadOldData();
        System.out.println("Stopped listening");
        sensorManager.unregisterListener(this);
        Toast.makeText(getApplicationContext(), "Stopped recording service",
                Toast.LENGTH_SHORT).show();
        mNM.cancel(NOTIFICATION);
        mNM.cancel(NOTIFICATION_TESTING);
        mNM.cancel(NOTIFICATION_SPEED);
        locationclient.removeLocationUpdates(this);
        inProgress = false;
        if (locationclient != null)
            locationclient.disconnect();
    }

    @Override
    public boolean stopService(Intent name) {
        uploadOldData();
        System.out.println("Stopped listening");
        sensorManager.unregisterListener(this);
        Toast.makeText(getApplicationContext(), "Stopped recording service",
                Toast.LENGTH_SHORT).show();
        mNM.cancel(NOTIFICATION);
        mNM.cancel(NOTIFICATION_TESTING);
        mNM.cancel(NOTIFICATION_SPEED);
        inProgress = false;
        locationclient.removeLocationUpdates(this);
        if (locationclient != null)
            locationclient.disconnect();
        return super.stopService(name);
    }

    public static Context getContext() {
        return CONTEXT;
    }

    private void showNotification() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                this).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Potholes").setAutoCancel(false)
                .setOngoing(true);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainPotholeActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainPotholeActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNM.notify(NOTIFICATION, mBuilder.build());

        // TODO: TESTING PURPOSES, REMOVE LATER
        mNotifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Lat").setContentText("Long")
                .setSmallIcon(R.drawable.ic_launcher).setAutoCancel(false)
                .setOngoing(true);
        mNM.notify(NOTIFICATION_TESTING, mNotifyBuilder.build());

		/*
		 * mNotifyBuilder = new NotificationCompat.Builder(this)
		 * .setContentTitle("Speed").setContentText("Meters per second")
		 * .setSmallIcon(R.drawable.ic_launcher).setAutoCancel(false)
		 * .setOngoing(true); mNM.notify(NOTIFICATION_SPEED,
		 * mNotifyBuilder.build());
		 */
    }

    // TODO: Get speed from accelerometer data, use it to change location.
    @Override
    public void onSensorChanged(SensorEvent event) {
        Long timestamp = System.currentTimeMillis();

        if (velocity == 0)
            velocity = 0.1; // At perfect standstills, this will make it check
        // once
        // a second

        Long tempOne = timestamp - oldTimestamp;
        Long tempTwo = (long) (1000 / (velocity * 10));

        if (tempOne.compareTo(tempTwo) >= 0) // Change 10 to 100? 1000 is 1 sec
        {
            // At 100 km/h, checks 278/second, once every 3.6ms
            // At 50km/h, checks 139/second, once every 7.2ms
            // At 5mk/h, checks 14/second, once every 72ms
            // At 1km/h, checks 2.8/second, once every 360ms

            oldTimestamp = timestamp;
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2] - SensorManager.GRAVITY_EARTH;

            double lat = (location.getLatitude());
            double lng = (location.getLongitude());

            AccelData data = new AccelData(timestamp, x, y, z, lat, lng);

            sensorData.add(data);
            if (sensorData.size() > 10000)
                uploadOldData();
        }
    }

    private void uploadOldData() {
        sensorDataOld = cloneList(sensorData);
        sensorData.clear();

        numberOfPotholes = sensorDataOld.size();

        new UpdateClientDetails().execute();
        new SavePotholeDetails().execute();
    }

    public static ArrayList<AccelData> cloneList(List<AccelData> list) {
        ArrayList<AccelData> clone = new ArrayList<AccelData>(list.size());
        for (AccelData item : list)
            clone.add(item);
        return clone;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub

    }

    private static Double distance(Location one, Location two) {
        int R = 6371000;
        Double dLat = toRad(two.getLatitude() - one.getLatitude());
        Double dLon = toRad(two.getLongitude() - one.getLongitude());
        Double lat1 = toRad(one.getLatitude());
        Double lat2 = toRad(two.getLatitude());
        Double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.sin(dLon / 2)
                * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        Double d = R * c;
        return d;
    }

    private static double toRad(Double d) {
        return d * Math.PI / 180;
    }

    // TODO: Not cancelling notification for uploading
    /**
     * Background Async Task to Save product Details
     * */
    class SavePotholeDetails extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //TODO: Remove this application when I release
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                    getApplicationContext())
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Uploading Potholes")
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setOngoing(true);
            mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // mId allows you to update the notification later on.
            mNM.notify(NOTIFICATION_UPLOAD, mBuilder.build());
        }

        /**
         * Saving product
         * */
        protected String doInBackground(String... args) {
            DefaultHttpClient _httpClient = new DefaultHttpClient();

            for (int i = 0; i < sensorDataOld.size(); i++) {
                // Building Parameters
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair(TAG_TIME, Long
                        .toString(sensorDataOld.get(i).getTimestamp())));
                params.add(new BasicNameValuePair(TAG_ACCEL_X, Double
                        .toString(sensorDataOld.get(i).getX())));
                params.add(new BasicNameValuePair(TAG_ACCEL_Y, Double
                        .toString(sensorDataOld.get(i).getY())));
                params.add(new BasicNameValuePair(TAG_ACCEL_Z, Double
                        .toString(sensorDataOld.get(i).getZ())));
                params.add(new BasicNameValuePair(TAG_CLIENT_ID, id));
                params.add(new BasicNameValuePair(TAG_CAR_TYPE, carType));
                params.add(new BasicNameValuePair(TAG_GPS_X, Double
                        .toString(sensorDataOld.get(i).getLat())));
                params.add(new BasicNameValuePair(TAG_GPS_Y, Double
                        .toString(sensorDataOld.get(i).getLng())));

                // sending modified data through http request
                // Notice that update product url accepts POST method
                //
                JSONObject json = jsonParser.makeHttpRequestWithClient(
                        _httpClient, url_update_pothole, "POST", params);

                // check json success tag
                try {
                    int success = json.getInt(TAG_SUCCESS);

                    if (success == 1) {
                        // successfully updated
                        if (i == sensorDataOld.size()) {
                            sensorDataOld.clear();
                            // Intent intent = getIntent();
                            // // send result code 100 to notify about product
                            // // update
                            // setResult(100, intent);
                            // finish();
                        }

                    } else {
                        // failed to update product
                    }

                    params.clear();
                    // params = null;
                    // jsonParser = null;
                    json = null;

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        protected void onPostExecute(String file_url) {
            // dismiss the dialog once product updated
            mNM.cancel(NOTIFICATION_UPLOAD);
        }
    }
    //TODO: Get this to work. Not updating database for some reason related to the UUID. Idk what, though

    class UpdateClientDetails extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected String doInBackground(String... args) {
            DefaultHttpClient _httpClient = new DefaultHttpClient();

            List<NameValuePair> params = new ArrayList<NameValuePair>();

            params.add(new BasicNameValuePair(TAG_CLIENT_ID, id));//"-1" as ID works
            params.add(new BasicNameValuePair(TAG_NUMBER_OF_POTHOLES, Integer
                    .toString(numberOfPotholes)));
            params.add(new BasicNameValuePair(TAG_USERNAME, Integer
                    .toString(-1)));

            Log.d(TAG, params.toString());

            JSONObject jsonClient = jsonParser.makeHttpRequestWithClient(_httpClient,
                    url_update_client, "POST", params);
            // check json success tag
            try {
                int success = jsonClient.getInt(TAG_SUCCESS);

                if (success == 1) {
                    // successfully updated
                    Log.d(TAG, jsonClient.getString("message"));
                    Log.d(TAG, "Updated Client; " + Integer.toString(numberOfPotholes));

                } else {
                    // failed to update product
                    Log.d(TAG, "Failed to update Client; " + jsonClient.getString("message"));
                }

                params.clear();
                // params = null;
                // jsonParser = null;
                jsonClient = null;

            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(String file_url) {
            // dismiss the dialog once product updated
        }
    }

    // TODO: Make a updateClient.php. The Clients db can have ID, number of
    // potholes,
    // distance traveled, facebook login info, username (selected by the
    // client), etc.

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "onConnected");
        if (locationclient != null && locationclient.isConnected()) {
            locationrequest = LocationRequest.create()
                    .setSmallestDisplacement(0).setFastestInterval(0)
                    .setInterval(0)
                    .setPriority(locationrequest.PRIORITY_HIGH_ACCURACY);
            locationclient.requestLocationUpdates(locationrequest, this);
        }
        if (oldLoc == null)
            oldLoc = locationclient.getLastLocation();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "onDisconnected");
        inProgress = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "onConnectionFailed");
        inProgress = false;
    }

    @Override
    public void onLocationChanged(Location locationArgs) {
        if (locationArgs != null) {
            Log.i(TAG, "Last Know Location:" + locationArgs.getLatitude() + ","
                    + locationArgs.getLongitude());

            location = locationArgs;

            // TODO: Send velocity to database?

            // TOOD: TESTING
            mNotifyBuilder.setContentTitle("Lat:" + location.getLatitude());
            mNotifyBuilder.setContentText("Lng:" + location.getLongitude());
            mNM.notify(NOTIFICATION_TESTING, mNotifyBuilder.build());

            mNotifyBuilder.setContentTitle("Speed");
            if (location.getTime() >= oldLoc.getTime() && oldLoc != location) {// TODO: TEST ME, fix
                // me
                //double temp = distance(location, oldLoc);
                //velocity = temp / ((newTime - oldTime) * 1000);
                long temp = (((long)oldLoc.distanceTo(location) / (location.getTime() - oldLoc.getTime()))*10);

                if(temp != 0)
                    velocity = 1000 / temp;
                else
                    velocity = 0.1;

                oldLoc = location;
                mNotifyBuilder.setContentText(Double.toString(velocity));
                mNM.notify(NOTIFICATION_SPEED, mNotifyBuilder.build());
            }
        }
    }
}

