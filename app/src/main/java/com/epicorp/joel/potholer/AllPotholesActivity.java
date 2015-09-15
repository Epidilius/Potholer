package com.epicorp.joel.potholer;

/**
 * Created by Joel on 9/15/2015.
 */


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class AllPotholesActivity extends ListActivity {
    // Creating JSON Parser object
    JSONParser jParser = new JSONParser();

    ArrayList<HashMap<String, String>> potholesList;

    // url to get all potholes list
    private static String url_all_potholes = "http://tokenfyt.com/PHPScripts/getPothole.php";

    // JSON Node names
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_POTHOLES = "potholes";
    private static final String TAG_PID = "_id";
    private static final String TAG_GPS_X = "gps_x";
    private static final String TAG_GPS_Y = "gps_y";
    private static final String TAG_SEVERITY = "severity";

    // potholes JSONArray
    JSONArray potholes = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hashmap for ListView
        potholesList = new ArrayList<HashMap<String, String>>();

        // Loading potholes in Background Thread
        new LoadAllPotholes().execute();
    }

    /**
     * Background Async Task to Load all pothole by making HTTP Request
     * */
    class LoadAllPotholes extends AsyncTask<String, String, String> {

        /**
         * Before starting background thread Show Progress Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
			/*
			 * pDialog = new ProgressDialog(AllPotholesActivity.this);
			 * pDialog.setMessage("Loading potholes. Please wait...");
			 * pDialog.setIndeterminate(false); pDialog.setCancelable(false);
			 * pDialog.show();
			 */
        }

        /**
         * getting All potholes from url
         * */
        protected String doInBackground(String... args) {
            // Building Parameters
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            // params.add(new BasicNameValuePair(TAG_TIME, Long
            // .toString(sensorData.get(i).getTimestamp())));
            // TODO: Add parameters here, also, find a way to only new info.
            // Maybe, save the old NE and SW, then pass them along as min or
            // max, depending on if its more or less than the new
            // getting JSON string from URL
            JSONObject json = jParser.makeHttpRequest(url_all_potholes, "GET",
                    params);

            // Check your log cat for JSON reponse
            Log.d("All Potholes: ", json.toString());

            try {
                // Checking for SUCCESS TAG
                int success = json.getInt(TAG_SUCCESS);

                if (success == 1) {
                    // potholes found
                    // Getting Array of Potholes
                    potholes = json.getJSONArray(TAG_POTHOLES);

                    // looping through All Potholes
                    for (int i = 0; i < potholes.length(); i++) {
                        JSONObject c = potholes.getJSONObject(i);
                        // TODO: Should I just load them onto the map here? Yes
                        // Storing each json item in variable
                        String id = c.getString(TAG_PID);
                        String gps_x = c.getString(TAG_GPS_X);
                        String gps_y = c.getString(TAG_GPS_Y);
                        String severity = c.getString(TAG_SEVERITY);

                        // creating new HashMap
                        HashMap<String, String> map = new HashMap<String, String>();

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
            return null;
        }

        /**
         * After completing background task Dismiss the progress dialog
         * **/
        protected void onPostExecute(String file_url) {
            // dismiss the dialog after getting all potholes
            // pDialog.dismiss();
            // updating UI from Background Thread
            runOnUiThread(new Runnable() {
                public void run() {
                    /**
                     * Updating parsed JSON data into ListView
                     * */
                    // TODO: Place the markers
                }
            });
        }
    }
}