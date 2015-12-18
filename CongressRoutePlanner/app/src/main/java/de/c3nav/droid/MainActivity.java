package de.c3nav.droid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private WifiReceiver wifiReceiver;
    private WebView webView;
    private MobileClient mobileClient;
    private Map<String, Integer> lastLevelValues = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View pView = LayoutInflater.from(this).inflate(R.layout.progressbar, null);
        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(pView);
        getSupportActionBar().getCustomView().setVisibility(View.GONE);

        mobileClient = new MobileClient();

        webView = (WebView) findViewById(R.id.webView);

        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUserAgentString("c3navClient/Android/" + BuildConfig.VERSION_CODE);
        webView.addJavascriptInterface(mobileClient, "mobileclient");

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView v, int progress) {
                if (progress < 100 && pView.getVisibility() == View.GONE) {
                    pView.setVisibility(View.VISIBLE);
                }
                if (progress == 100) {
                    pView.setVisibility(View.GONE);
                }
            }

        });

        String url_to_call = BuildConfig.WEB_URL;
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            Uri.Builder tmp_uri = data.buildUpon();
            tmp_uri.scheme("https");
            url_to_call = tmp_uri.build().toString();
        }

        webView.loadUrl(url_to_call);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        wifiManager.startScan();
    }

    class MobileClient {
        private JSONArray nearbyStations;

        @JavascriptInterface
        public String getNearbyStations() {
            return this.nearbyStations.toString();
        }

        public void setNearbyStations(JSONArray nearbyStations) {
            this.nearbyStations = nearbyStations;
        }

        @JavascriptInterface
        public void scanNow() {
            wifiManager.startScan();
        }
    }

    class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            webView.post(new Runnable() {
                @Override
                public void run() {

                    List<ScanResult> wifiList = wifiManager.getScanResults();
                    JSONArray ja = new JSONArray();
                    Map<String, Integer> newLevelValues = new HashMap<String, Integer>();
                    for (ScanResult result : wifiList) {
                        JSONObject jo = new JSONObject();
                        try {
                            jo.put("bssid", result.BSSID);
                            jo.put("ssid", result.SSID);
                            jo.put("level", result.level);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                if (SystemClock.elapsedRealtime() - result.timestamp / 1000 > 1000) {
                                    continue;
                                }
                                jo.put("last", SystemClock.elapsedRealtime() - result.timestamp / 1000);
                            } else {
                                // Workaround for older devices: If the signal level did not change
                                // at all since the last scan, we will assume that it is a cached
                                // value and should not be used.
                                newLevelValues.put(result.BSSID, result.level);
                                if (lastLevelValues.containsKey(result.BSSID) && lastLevelValues.get(result.BSSID) == result.level) {
                                    continue;
                                }
                            }
                            ja.put(jo);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    mobileClient.setNearbyStations(ja);
                    lastLevelValues = newLevelValues;
                    webView.loadUrl("javascript:nearby_stations_available();");
                }
            });
        }
    }
}
