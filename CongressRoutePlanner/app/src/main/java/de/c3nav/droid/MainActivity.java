package de.c3nav.droid;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.transition.AutoTransition;
import android.support.transition.Scene;
import android.support.transition.Transition;
import android.support.transition.TransitionManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final int PERM_REQUEST = 1;
    private WifiManager wifiManager;
    private WebView webView;
    private MobileClient mobileClient;
    private Map<String, Integer> lastLevelValues = new HashMap<>();
    private boolean permAsked = false;
    private WifiReceiver wifiReceiver;
    protected CustomSwipeToRefresh swipeLayout;

    private LinearLayout splashScreen;
    private VideoView logoAnimView;
    private boolean hasSplashScreen;
    private boolean logoAnimFinished = false;
    private boolean splashScreenStarted = false;
    private boolean splashScreenPaused = false;
    private boolean splashScreenDone = false;
    private boolean initialPageLoaded = false;
    private boolean circularWebViewRevealStarted = false;

    public int logoWidth = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        Set<String> intentCategories = intent.getCategories();
        boolean activityStartedFromLauncher = Intent.ACTION_MAIN.equals(intent.getAction())
                && intentCategories != null && intentCategories.contains(Intent.CATEGORY_LAUNCHER);
        boolean activityStartedFromURLFiler = Intent.ACTION_VIEW.equals(intent.getAction())
                && intentCategories != null && (
                        intentCategories.contains(Intent.CATEGORY_DEFAULT) ||
                        intentCategories.contains(Intent.CATEGORY_BROWSABLE) );

        mobileClient = new MobileClient();

        splashScreen = findViewById(R.id.splashScreen);
        logoAnimView = findViewById(R.id.logoAnimation);

        webView = findViewById(R.id.webView);
        webView.setBackgroundColor(Color.TRANSPARENT);
        swipeLayout = findViewById(R.id.swipe_container);


        if (activityStartedFromLauncher) {
            showSplash();
        } else {
            skipSplash();
        }

        swipeLayout.setColorSchemeResources(R.color.colorPrimary);
        swipeLayout.setEnabled(true);
        swipeLayout.setOnRefreshListener(
            new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    webView.reload();
                }
            }
        );

        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUserAgentString("c3navClient/Android/" + BuildConfig.VERSION_CODE);
        webView.addJavascriptInterface(mobileClient, "mobileclient");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeLayout.setRefreshing(false);
                initialPageLoaded = true;
                Log.d("c3navWebView", "loading ended");
                maybeEndSplash();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                swipeLayout.setRefreshing(true);
                Log.d("c3navWebView", "loading started");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                if (Uri.parse(BuildConfig.WEB_URL).getHost().equals(u.getHost())) {
                    List<String> pathSegments = u.getPathSegments();
                    if (pathSegments.isEmpty() || !pathSegments.get(0).equals("api")) {
                        return false;
                    }
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, u);
                startActivity(intent);
                return true;
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, String host, String realm) {
                LayoutInflater li = MainActivity.this.getLayoutInflater();
                View dialogview = li.inflate(R.layout.dialog_auth, null);
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setView(dialogview);
                final EditText user = (EditText) dialogview.findViewById(R.id.etUser);
                final EditText pass = (EditText) dialogview.findViewById(R.id.etPassword);

                alertDialogBuilder.setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                handler.proceed(user.getText().toString(), pass.getText().toString());
                                dialog.dismiss();
                            }
                        });

                alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();

                    }
                });
                alertDialogBuilder.show();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsBeforeUnload(WebView view, String url, String message, final JsResult result) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setMessage(message);
                alertDialogBuilder.setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                result.confirm();
                                dialog.dismiss();
                            }
                        });

                alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        result.cancel();
                        swipeLayout.setRefreshing(false);
                    }
                });
                alertDialogBuilder.show();
                return true;
            }
        });

        String url_to_call = BuildConfig.WEB_URL;
        Uri data = intent.getData();
        if (data != null) {
            Uri.Builder tmp_uri = data.buildUpon();
            tmp_uri.scheme("https");
            url_to_call = tmp_uri.build().toString();
        }

        webView.loadUrl(url_to_call);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
    }

    protected void showSplash() {
        splashScreenStarted = true;
        logoAnimView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                logoAnimFinished = true;
                skipSplash();
                return true;
            }
        });
        logoAnimView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                logoWidth = (int) (((float) mp.getVideoWidth())/mp.getVideoHeight()*50f);
                logoAnimFinished = true;

                // keep the logo for 500 more ms before checking whether the webview is loaded (it's probably not)
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        maybeEndSplash();
                    }
                }, 500);
            }
        });
        playSplashVideo();
    }

    protected void playSplashVideo() {
        logoAnimView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.logoanim));
        logoAnimView.start();
    }

    protected boolean maybeEndSplash() {
        if (splashScreenDone || !logoAnimFinished || !initialPageLoaded) {
            return false;
        }
        endSplash();
        return true;
    }

    protected void endSplash() {
        AutoTransition mySwapTransition = new AutoTransition();
        mySwapTransition.addListener(new Transition.TransitionListener() {
            private boolean transitionEnded = false;

            @Override
            public void onTransitionStart(Transition transition) { }

            @Override
            public void onTransitionEnd(Transition transition) {
                // remove animated logo because the stuff in the background is still visible
                splashScreen.setVisibility(View.GONE);
                circularWebViewReveal();
            }

            @Override
            public void onTransitionCancel(Transition transition) {}

            @Override
            public void onTransitionPause(Transition transition) { }

            @Override
            public void onTransitionResume(Transition transition) { }
        });

        TransitionManager.go(new Scene((ViewGroup) splashScreen.getParent()), mySwapTransition);
        splashScreen.setGravity(Gravity.TOP);
        int dip5 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        splashScreen.setPadding(0, dip5, 0, dip5);
        logoAnimView.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
        logoAnimView.requestLayout();
    }

    protected void circularWebViewReveal() {
        if (circularWebViewRevealStarted) return;
        circularWebViewRevealStarted = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // center of the clipping circle
            int cx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, logoWidth/2, getResources().getDisplayMetrics());
            int cy = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 27, getResources().getDisplayMetrics());

            // webview dimensions
            int width = swipeLayout.getWidth();
            int height = swipeLayout.getHeight();

            // get the final radius for the clipping circle
            float finalRadius = (float) Math.hypot(width - cx, height - cy);

            // create the animation
            Animator anim = ViewAnimationUtils.createCircularReveal(swipeLayout, cx, cy, 0f, finalRadius);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Log.d("c3nav", "splash animation done");
                    splashScreenDone = true;
                }
            });
            anim.start();
        } else {
            // no animation, maybe insert some alternate animation?
        }
    }

    protected void skipSplash() {
        splashScreenDone = true;
        splashScreen.setVisibility(View.GONE);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        wifiManager.startScan();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        if (splashScreenPaused && !splashScreenDone) {
            skipSplash();
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver(wifiReceiver);
        if (splashScreenStarted && !splashScreenDone) {
            splashScreenPaused = true;
        }
        super.onPause();
    }

    class MobileClient {
        private JSONArray nearbyStations;

        @JavascriptInterface
        public String getNearbyStations() {
            if (this.nearbyStations != null) {
                return this.nearbyStations.toString();
            } else {
                return "[]";
            }
        }

        public void setNearbyStations(JSONArray nearbyStations) {
            this.nearbyStations = nearbyStations;
        }

        @JavascriptInterface
        public int getAppVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public void scanNow() {
            wifiManager.startScan();
        }

        @JavascriptInterface
        public void shareUrl(String url) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            i.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        }

        @JavascriptInterface
        public void createShortcut(String url, String title) {
            Intent shortcutIntent = new Intent(getApplicationContext(),
                    MainActivity.class);
            shortcutIntent.setAction(Intent.ACTION_MAIN);
            shortcutIntent.setData(Uri.parse(url));

            Intent addIntent = new Intent();
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(getApplicationContext(),
                            R.mipmap.ic_launcher_35c3));
            addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            getApplicationContext().sendBroadcast(addIntent);

            Toast.makeText(MainActivity.this, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share:
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                i.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                startActivity(Intent.createChooser(i, getString(R.string.share)));
                return true;
            case R.id.refresh:
                webView.loadUrl(BuildConfig.WEB_URL);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        if (!permAsked) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERM_REQUEST);
                            permAsked = true;
                        }
                        return;
                    }
                    List<ScanResult> wifiList = wifiManager.getScanResults();
                    JSONArray ja = new JSONArray();
                    Map<String, Integer> newLevelValues = new HashMap<String, Integer>();
                    for (ScanResult result : wifiList) {
                        JSONObject jo = new JSONObject();
                        try {
                            jo.put("bssid", result.BSSID);
                            jo.put("ssid", result.SSID);
                            jo.put("level", result.level);
                            jo.put("frequency", result.frequency);

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
                                    Log.d("scan result", "Discard " + result.BSSID + " because level did not change");
                                    continue;
                                }
                            }
                            ja.put(jo);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.d("scan result", ja.toString());
                    mobileClient.setNearbyStations(ja);
                    lastLevelValues = newLevelValues;
                    webView.loadUrl("javascript:nearby_stations_available();");
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onAttachedToWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }
}
