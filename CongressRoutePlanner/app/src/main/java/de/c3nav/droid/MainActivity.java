package de.c3nav.droid;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.MediaPlayer;
import android.net.MacAddress;
import android.net.ParseException;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import androidx.lifecycle.Lifecycle;
import com.google.android.material.navigation.NavigationView;

import androidx.transition.AutoTransition;
import androidx.transition.Scene;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static android.net.wifi.ScanResult.*;
import static android.net.wifi.rtt.ResponderConfig.RESPONDER_AP;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    //Actions
    public static final String ACTION_CURRENT_LOCATION = "de.c3nav.droid.action.CURRENT_LOCATION";
    public static final String ACTION_CONTROL_PANEL = "de.c3nav.droid.action.CONTROL_PANEL";
    public static final String ACTION_EDITOR = "de.c3nav.droid.action.EDITOR";

    //Request Codes
    public static final int PERM_REQUEST = 1;
    public final static int SETTINGS_REQUEST = 2;

    //Activity State Keys
    public static final String STATE_SPLASHSCREEN = "splashScreenState";
    public static final String STATE_URI = "urlState";

    private PowerManager powerManager;
    private DrawerLayout mDrawerLayout;
    private NavigationView navigationView;
    private Menu navigationMenu;
    private Toolbar toolbar;
    private WebView webView;
    private MobileClient mobileClient;
    private boolean locationPermissionRequested;
    private List<String> permissionCache = null;
    protected CustomSwipeToRefresh swipeLayout;

    private WifiManager wifiManager;
    private WifiReceiver wifiReceiver;
    private List<SuggestedWifiPeer> suggestedWifiPeers = new ArrayList<>();
    private Map<String, ScanResult> lastWifiScanResults = new HashMap<>();
    private Map<String, RangingResult> lastWifiRangingResults = new HashMap<>();

    private LinearLayout splashScreen;
    private VideoView logoAnimView;
    private LinearLayout logoScreen;
    private TextView logoScreenMessage;
    private EditText authUsername;
    private EditText authPassword;
    private TextView authMessage;
    private Button authLoginButton;

    private CachedUserPermissions cachedUserPermissions;
    private boolean loggedIn = false;
    private boolean inEditor = false;
    private boolean wifiMeasurementRunning = false;
    private boolean hasChangeSet = false;

    private TextView navHeaderTitle;
    private TextView navHeaderSubtitle;

    private MenuItem accountLink;
    private MenuItem editorChangesLink;
    private MenuItem editorDashboardLink;
    private MenuItem editorLink;
    private MenuItem controlPanelLink;

    private boolean logoAnimFinished = false;
    private boolean splashScreenStarted = false;
    private boolean splashScreenPaused = false;
    private boolean splashScreenDone = false;
    private boolean initialPageLoaded = false;
    private boolean splashScreenFadeoutStarted = false;

    private boolean httpAuthNeeded = false;
    private HttpAuthHandler lastAuthHandler = null;
    private boolean logoScreenIsVisible = false;
    private boolean loginScreenIsActive = false;

    private SharedPreferences sharedPrefs;
    private boolean settingKeepOnTop = true;
    private boolean settingKeepScreenOn = true;
    private boolean settingUseWifiAndBluetoothLocating = true;
    private Integer settingWifiScanRate = 30;
    private boolean settingDeveloperModeEnabled = false;
    private String settingDeveloperInstanceUrl = "";
    private String settingDeveloperHttpUser = null;
    private String settingDeveloperHttpPassword = null;

    protected Uri instanceBaseUrl;

    protected BeaconManager beaconManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            splashScreenDone = savedInstanceState.getBoolean(STATE_SPLASHSCREEN, false);
        }

        instanceBaseUrl = Uri.parse(BuildConfig.WEB_URL);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);


        Intent intent = getIntent();
        Set<String> intentCategories = intent.getCategories();
        boolean activityStartedFromLauncher = Intent.ACTION_MAIN.equals(intent.getAction())
                && intentCategories != null && intentCategories.contains(Intent.CATEGORY_LAUNCHER);
        boolean activityStartedFromURLHandler = Intent.ACTION_VIEW.equals(intent.getAction())
                && intentCategories != null && (
                intentCategories.contains(Intent.CATEGORY_DEFAULT) ||
                        intentCategories.contains(Intent.CATEGORY_BROWSABLE));

        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sharedPrefs = context.getSharedPreferences(PreferenceManager.getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE);
        } else {
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        }
        locationPermissionRequested = sharedPrefs.getBoolean(getString(R.string.location_permission_requested_key), false);
        if (BuildConfig.DEBUG && sharedPrefs.getBoolean(getString(R.string.developer_mode_enabled_key), false)) {
            settingDeveloperInstanceUrl = sharedPrefs.getString(getString(R.string.developer_instance_url_key), "");

            if (!settingDeveloperInstanceUrl.isEmpty()) {
                try {
                    Uri devInstanceUri = Uri.parse(settingDeveloperInstanceUrl);
                    instanceBaseUrl = devInstanceUri;
                } catch (ParseException e) {
                    Log.d("developerSettings", "failed to parse developerInstanceUrl \"" + settingDeveloperInstanceUrl + "\", ignoring");
                }
            }
        }


        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.addRangeNotifier((beacons, region) -> {
            if (beacons.isEmpty()) return;
            JSONArray ja = new JSONArray();
            try {
                for (Beacon beacon : beacons) {
                    JSONObject jo = new JSONObject();
                    jo.put("uuid", region.getUniqueId());
                    jo.put("major", beacon.getId2().toInt());
                    jo.put("minor", beacon.getId3().toInt());
                    jo.put("distance", beacon.getDistance());
                    jo.put("last_seen", beacon.getLastCycleDetectionTimestamp());
                    ja.put(jo);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mobileClient.setNearbyBeacons(ja);
            webView.post(() -> MainActivity.this.evaluateJavascript("ibeacon_results_available();"));
        });
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        mDrawerLayout = findViewById(R.id.drawer_layout);

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem item) {
                        Intent browserIntent;
                        Uri uri;
                        mDrawerLayout.closeDrawers();
                        int itemId = item.getItemId();
                        if (itemId == R.id.accountLink) {
                            if (loggedIn) {
                                uri = MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/account/").build();
                            } else {
                                uri = MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/login").build();

                                if (webView.getUrl() != null)
                                    uri = uri.buildUpon().appendQueryParameter("next", Uri.parse(webView.getUrl()).getPath()).build();
                            }
                            MainActivity.this.evaluateJavascript("window.openInModal ? openInModal('" + uri.toString() + "') : window.location='" + uri.toString() + "';");
                            return true;
                        } else if (itemId == R.id.editorChangesLink) {
                            webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/editor/changeset/").build().toString());
                            return true;
                        } else if (itemId == R.id.editorDashboardLink) {
                            webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/editor/user/").build().toString());
                            return true;
                        } else if (itemId == R.id.mapLink) {
                            webView.loadUrl(instanceBaseUrl.toString());
                            return true;
                        } else if (itemId == R.id.editorLink) {
                            webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/editor/").build().toString());
                            return true;
                        } else if (itemId == R.id.controlPanelLink) {
                            webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/control/").build().toString());
                            return true;
                        } else if (itemId == R.id.apiLink) {
                            browserIntent = new Intent(Intent.ACTION_VIEW, MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/api/").build());
                            startActivity(browserIntent);
                            return true;
                        } else if (itemId == R.id.fediLink) {
                            browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chaos.social/@c3nav"));
                            startActivity(browserIntent);
                            return true;
                        } else if (itemId == R.id.githubLink) {
                            browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/c3nav/"));
                            startActivity(browserIntent);
                            return true;
                        } else if (itemId == R.id.aboutLink) {
                            uri = MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/about/").build();
                            MainActivity.this.evaluateJavascript("window.openInModal ? openInModal('" + uri.toString() + "') : window.location='" + uri.toString() + "';");
                            return true;
                        } else if (itemId == R.id.settingsButton) {
                            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                ActivityCompat.startActivityForResult(MainActivity.this, settingsIntent, SETTINGS_REQUEST, ActivityOptionsCompat.makeSceneTransitionAnimation(MainActivity.this).toBundle());
                            } else {
                                startActivityForResult(settingsIntent, SETTINGS_REQUEST);
                            }

                            return false;
                        }
                        return false;
                    }
                });

        mobileClient = new MobileClient();

        splashScreen = findViewById(R.id.splashScreen);
        logoAnimView = findViewById(R.id.logoAnimation);

        logoScreen = findViewById(R.id.logoScreen);
        logoScreenMessage = findViewById(R.id.logoScreenMessage);
        authUsername = findViewById(R.id.authUsername);
        authPassword = findViewById(R.id.authPassword);
        authMessage = findViewById(R.id.authMessage);
        authLoginButton = findViewById(R.id.authLoginButton);

        authLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.handleLoginScreenSubmit();
            }
        });

        authPassword.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND
                        || keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    MainActivity.this.handleLoginScreenSubmit();
                    return true;
                }
                return false;
            }
        });

        View headerLayout = navigationView.getHeaderView(0);
        navHeaderTitle = headerLayout.findViewById(R.id.title);
        navHeaderSubtitle = headerLayout.findViewById(R.id.subtitle);

        navigationMenu = navigationView.getMenu();
        accountLink = navigationMenu.findItem(R.id.accountLink);
        editorChangesLink = navigationMenu.findItem(R.id.editorChangesLink);
        editorDashboardLink = navigationMenu.findItem(R.id.editorDashboardLink);
        editorLink = navigationMenu.findItem(R.id.editorLink);
        controlPanelLink = navigationMenu.findItem(R.id.controlPanelLink);

        webView = findViewById(R.id.webView);
        swipeLayout = findViewById(R.id.swipe_container);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (!splashScreenDone && activityStartedFromLauncher) {
            mDrawerLayout.closeDrawers();
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            //showSplash();
            showLogoScreen();
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
        webView.getSettings().setUserAgentString(String.format(Locale.ENGLISH, "c3navClient/Android/%d/%d", BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT));
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(mobileClient, "mobileclient");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeLayout.setRefreshing(false);
                initialPageLoaded = true;
                Log.d("c3navWebView", "loading ended");
                maybeEndSplash();
                maybeHideLoginOrLogoScreen();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                swipeLayout.setRefreshing(true);
                if (isWifiMeasurementRunning()) setWifiMeasurementRunning(false);
                Log.d("c3navWebView", "loading started");
            }

            private boolean shouldOverrideUrl(final Uri uri) {
                if (MainActivity.this.instanceBaseUrl.getHost().equals(uri.getHost())) {
                    List<String> pathSegments = uri.getPathSegments();
                    if (pathSegments.isEmpty() || !pathSegments.get(0).equals("api")) {
                        return false;
                    }
                }
                ExternalUrlDialog dialog = new ExternalUrlDialog();
                Bundle args = new Bundle();
                args.putString(ExternalUrlDialog.ARG_URL, uri.toString());
                dialog.setArguments(args);
                dialog.show(getSupportFragmentManager(), "externalUrl");
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && shouldOverrideUrl(Uri.parse(url))) || super.shouldOverrideUrlLoading(view, url);
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldOverrideUrl(request.getUrl()) || super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, String host, String realm) {
                if (BuildConfig.DEBUG && settingDeveloperModeEnabled
                        && settingDeveloperHttpUser != null && !settingDeveloperHttpUser.isEmpty()
                        && settingDeveloperHttpPassword != null && !settingDeveloperHttpPassword.isEmpty()) {
                    handler.proceed(settingDeveloperHttpUser, settingDeveloperHttpPassword);
                } else {
                    httpAuthNeeded = true;
                    lastAuthHandler = handler;
                    maybeShowLoginScreen();
                }
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

        updateSettings();
        hasLocationPermission();
        cachedUserPermissions = new CachedUserPermissions();
        updateDynamicShortcuts();

        String url_to_call = instanceBaseUrl.toString();
        Uri data = intent.getData();

        if (intent.getAction().equals(ACTION_CONTROL_PANEL)) {
            url_to_call = instanceBaseUrl.buildUpon().appendPath("control").build().toString();
        } else if (intent.getAction().equals(ACTION_CURRENT_LOCATION)) {
            mobileClient.setCurrentLocationRequested(true);
        } else if (intent.getAction().equals(ACTION_EDITOR)) {
            url_to_call = instanceBaseUrl.buildUpon().appendPath("editor").build().toString();
        } else if (savedInstanceState != null && savedInstanceState.getString(STATE_URI) != null) {
            Uri savedUri = Uri.parse(savedInstanceState.getString(STATE_URI));
            if (savedUri.getHost().equals(instanceBaseUrl.getHost())) {
                url_to_call = savedUri.toString();
            }
        } else if (data != null) {
            Uri.Builder tmp_uri = data.buildUpon();
            tmp_uri.scheme("https");
            List<String> pathSegments = data.getPathSegments();
            if (!pathSegments.isEmpty() && pathSegments.get(0).equals("embed")) {
                tmp_uri.path("");
                for (String pathSegment : pathSegments) {
                    if (pathSegment.equals("embed")) continue;
                    tmp_uri.appendPath(pathSegment);
                }
                Log.d("c3navIntendUriBuilder", "converted embed URL to normal URL, orignal URL: " + data.toString());
            }
            url_to_call = tmp_uri.build().toString();
            Log.d("c3navIntendUriBuilder", "final url: " + url_to_call);
        }

        CookieManager.getInstance().setCookie(instanceBaseUrl.toString(), "c3nav_language=" + Locale.getDefault().getLanguage());
        webView.loadUrl(url_to_call);

    }

    protected void updateSettings() {
        settingKeepOnTop = sharedPrefs.getBoolean(getString(R.string.keep_on_top_key), true);
        settingKeepScreenOn = sharedPrefs.getBoolean(getString(R.string.keep_screen_on_key), true);
        if (settingUseWifiAndBluetoothLocating != sharedPrefs.getBoolean(getString(R.string.use_wifi_bt_locating_key), true)) {
            Log.d("c3nav-settings", "settingUseWifiAndBluetoothLocating updated");
            settingUseWifiAndBluetoothLocating = sharedPrefs.getBoolean(getString(R.string.use_wifi_bt_locating_key), true);
            if (settingUseWifiAndBluetoothLocating) {
                startScan();
            } else {
                mobileClient.setNearbyStations(null);
            }
            MainActivity.this.evaluateJavascript("nearby_stations_available();");
        }

        settingWifiScanRate = Integer.parseInt(sharedPrefs.getString(getString(R.string.wifi_scan_rate_key), "30"));

        setWindowFlags();

        if (BuildConfig.DEBUG) {
            settingDeveloperModeEnabled = sharedPrefs.getBoolean(getString(R.string.developer_mode_enabled_key), false);
            String newSettingDeveloperInstanceUrl = "";
            if (settingDeveloperModeEnabled) {
                newSettingDeveloperInstanceUrl = sharedPrefs.getString(getString(R.string.developer_instance_url_key), "");
                settingDeveloperHttpUser = sharedPrefs.getString(getString(R.string.developer_http_user_key), "");
                settingDeveloperHttpPassword = sharedPrefs.getString(getString(R.string.developer_http_password_key), "");
            } else {
                settingDeveloperHttpUser = null;
                settingDeveloperHttpPassword = null;
            }

            if (!settingDeveloperInstanceUrl.equals(newSettingDeveloperInstanceUrl)) {
                recreate();
            }
        }
    }

    protected void showSplash() {
        splashScreenStarted = true;
        logoAnimView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                logoAnimFinished = true;
                if (!splashScreenDone) skipSplash();
                return true;
            }
        });
        logoAnimView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                logoAnimFinished = true;

                // keep the logo for 500 more ms before checking whether the webview is loaded (it's probably not)
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        maybeEndSplash();
                    }
                }, 500);
                maybeShowLoginScreen();
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
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                // remove animated logo because the stuff in the background is still visible
                fadeoutSplashScreen();
            }

            @Override
            public void onTransitionCancel(Transition transition) {
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }
        });

        TransitionManager.go(new Scene((ViewGroup) splashScreen.getParent()), mySwapTransition);
        splashScreen.setGravity(Gravity.TOP | Gravity.CENTER);
        logoAnimView.getLayoutParams().height = (int) toolbar.getHeight();
        logoAnimView.requestLayout();
    }

    protected void fadeoutSplashScreen() {
        if (splashScreenFadeoutStarted) return;
        splashScreenFadeoutStarted = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // center of the clipping circle
            int cx = (int) swipeLayout.getWidth() / 2;
            int cy = (int) toolbar.getHeight() / 2;

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
                    mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                    splashScreenDone = true;
                    unloadSplashVideo();
                    checkLocationPermission();
                }
            });
            splashScreen.setVisibility(View.GONE);
            anim.start();
        } else {
            splashScreen.animate()
                    .alpha(0f)
                    .setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            splashScreen.setVisibility(View.GONE);
                            splashScreen.setAlpha(1f);
                        }
                    });
        }
    }

    protected void unloadSplashVideo() {
        if (logoAnimView != null) {
            ((ViewManager) logoAnimView.getParent()).removeView(logoAnimView);
            logoAnimView = null;
        }
    }

    protected void skipSplash(boolean checkLogin) {
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        splashScreenDone = true;
        splashScreen.setVisibility(View.GONE);
        unloadSplashVideo();
        if (checkLogin) maybeShowLoginScreen();
        checkLocationPermission();
    }

    protected void skipSplash() {
        skipSplash(true);
    }

    protected void showLogoScreen() {
        if (!splashScreenDone) {
            logoScreen.setVisibility(View.VISIBLE);
            skipSplash(false);
        } else if (logoScreenIsVisible || loginScreenIsActive) {
            TransitionManager.go(new Scene((ViewGroup) logoScreen.getParent()), new AutoTransition());
        } else {
            TransitionManager.go(new Scene((ViewGroup) logoScreen.getParent()), new Slide(Gravity.RIGHT));
        }
        logoScreen.setVisibility(View.VISIBLE);
        logoScreenMessage.setVisibility(View.GONE);
        authUsername.setVisibility(View.GONE);
        authPassword.setVisibility(View.GONE);
        authMessage.setVisibility(View.GONE);
        authLoginButton.setVisibility(View.GONE);
        logoScreenIsVisible = true;
    }

    protected void hideLogoScreen() {
        TransitionManager.go(new Scene((ViewGroup) logoScreen.getParent()), new Slide(Gravity.LEFT));
        logoScreen.setVisibility(View.GONE);
        logoScreenMessage.setVisibility(View.GONE);
        logoScreenIsVisible = false;
    }

    protected void showLoginScreen(String message) {
        if (!logoScreenIsVisible) showLogoScreen();
        logoScreenMessage.setText(R.string.auth_title);
        authMessage.setText(message);
        authUsername.setEnabled(true);
        authPassword.setEnabled(true);
        authLoginButton.setEnabled(true);

        if (loginScreenIsActive) return;

        logoScreen.postDelayed(new Runnable() {
            @Override
            public void run() {
                TransitionManager.go(new Scene((ViewGroup) logoScreen.getParent()), new AutoTransition());
                logoScreenMessage.setVisibility(View.VISIBLE);
                authUsername.setVisibility(View.VISIBLE);
                authPassword.setVisibility(View.VISIBLE);
                authMessage.setVisibility(View.VISIBLE);
                authLoginButton.setVisibility(View.VISIBLE);
            }
        }, 500);
        loginScreenIsActive = true;
    }

    protected void showLoginScreen(int message) {
        showLoginScreen(getString(message));
    }

    protected void showLoginScreen() {
        showLoginScreen("");
    }

    protected void hideLoginScreen() {
        hideLogoScreen();
        authUsername.setVisibility(View.GONE);
        authPassword.setVisibility(View.GONE);
        authMessage.setVisibility(View.GONE);
        authLoginButton.setVisibility(View.GONE);
        loginScreenIsActive = false;
    }

    protected void maybeShowLoginScreen() {
        if (httpAuthNeeded && (splashScreenDone || logoAnimFinished)) {
            if (loginScreenIsActive) {
                showLoginScreen(R.string.auth_error);
            } else {
                showLoginScreen();
            }
        }
    }

    protected void maybeHideLoginOrLogoScreen() {
        if (loginScreenIsActive && initialPageLoaded) {
            hideLoginScreen();
        } else if (logoScreenIsVisible && initialPageLoaded) {
            hideLogoScreen();
        }
    }

    protected void handleLoginScreenSubmit() {
        if (lastAuthHandler != null) {
            lastAuthHandler.proceed(authUsername.getText().toString(), authPassword.getText().toString());
            lastAuthHandler = null;
            authLoginButton.setEnabled(false);
            authUsername.setEnabled(false);
            authPassword.setEnabled(false);
        } else {
            hideLoginScreen();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(STATE_SPLASHSCREEN, splashScreenDone);
        savedInstanceState.putString(STATE_URI, webView.getUrl());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("lifecycleEvents", "onResume called");
        evaluateJavascript("if (window.mobileclientOnResume) {mobileclientOnResume()};");
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        if (checkLocationPermission(false, true)) startScan();
        if (splashScreenPaused && !splashScreenDone) {
            skipSplash();
        }
    }

    @Override
    protected void onPause() {
        Log.d("lifecycleEvents", "onPause called");
        unregisterReceiver(wifiReceiver);
        if (splashScreenStarted && !splashScreenDone) {
            splashScreenPaused = true;
        }
        evaluateJavascript("if (mobileclientOnPause) {mobileclientOnPause()};");
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("lifecycleEvents", "onActivityResult called");

        switch (requestCode) {
            case SETTINGS_REQUEST:
                Log.d("onActivityResult", "settings activity finished with result code " + resultCode);
                updateSettings();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERM_REQUEST:
                Set<String> perms = new HashSet<>(permissionCache);
                for (int i = 0; i < permissions.length; i++) {
                    String perm = permissions[i];
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        perms.add(perm);
                    } else {
                        perms.remove(perm);
                    }
                }
                permissionCache = new ArrayList<>(perms);
                if (!perms.isEmpty()) {
                    if (!settingUseWifiAndBluetoothLocating) {
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putBoolean(getString(R.string.use_wifi_bt_locating_key), true);
                        editor.apply();
                        settingUseWifiAndBluetoothLocating = true;
                    }
                    // let the js know we have location permission now and start a single scan
                    evaluateJavascript("nearby_stations_available();");
                    startScan();
                }
        }
    }

    protected boolean isLocationPermissionRequested() {
        return locationPermissionRequested;
    }

    private void setLocationPermissionRequested(boolean locationPermissionRequested) {
        this.locationPermissionRequested = locationPermissionRequested;
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(getString(R.string.location_permission_requested_key), locationPermissionRequested);
        editor.apply();
    }

    protected boolean checkLocationPermission(boolean requestPermission, boolean ignoreCache) {
        if (!settingUseWifiAndBluetoothLocating) {
            if (requestPermission) {
                new WifiLocationDisabledDialog().show(getSupportFragmentManager(), null);
            } else {
                return false;
            }
        }

        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
        }

        List<String> missingPermissions = new ArrayList<>();
        if (ignoreCache || permissionCache == null) {
            List<String> grantedPermissions = new ArrayList<>();
            for (String perm : permissionsToRequest) {
                int res = ContextCompat.checkSelfPermission(MainActivity.this, perm);
                if (res == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(perm);
                } else {
                    missingPermissions.add(perm);
                }
            }
            permissionCache = grantedPermissions;
        }
        if (!missingPermissions.isEmpty()) {
            if (requestPermission) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        missingPermissions.toArray(new String[0]),
                        PERM_REQUEST);
            }
            return false;
        }
        return settingUseWifiAndBluetoothLocating;
    }

    protected boolean checkLocationPermission(boolean requestPermission) {
        return checkLocationPermission(requestPermission, false);
    }

    protected boolean checkLocationPermission() {
        return checkLocationPermission(!this.isLocationPermissionRequested() && splashScreenDone);
    }

    protected boolean hasLocationPermission() {
        return checkLocationPermission(false);
    }

    protected void startScan() {
        if (!settingUseWifiAndBluetoothLocating) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (!powerManager.isScreenOn()) return;
        } else {
            if (!powerManager.isInteractive()) return;
        }
        if (!hasLocationPermission()) return;
        Log.d("c3navWifiScanner", "startScan triggered");
        wifiManager.startScan();

        // Range up to 10 times within interval, result in seconds
        int rangeRate = Integer.parseInt(MainActivity.this.sharedPrefs.getString(getString(R.string.wifi_scan_rate_key), "30")) / 10;
        // Lower limit if scan rate is set low. Scanning more than once per 2s is not really feasible
        rangeRate = Math.max(rangeRate, 2);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                MainActivity.this.startWifiRanging();
            }
        }, 0, rangeRate * 1000L);
    }

    protected void setInEditor(boolean inEditor) {
        boolean inEditorOld = this.inEditor;
        this.inEditor = inEditor;

        if (inEditorOld != this.inEditor) {
            setWindowFlags();
        }
    }

    public boolean isInEditor() {
        return this.inEditor;
    }

    protected void setWifiMeasurementRunning(boolean wifiMeasurementRunning) {
        boolean wifiMeasurementRunningOld = this.wifiMeasurementRunning;
        this.wifiMeasurementRunning = wifiMeasurementRunning;

        if (wifiMeasurementRunningOld != this.wifiMeasurementRunning) {
            setWindowFlags();
        }
    }

    public boolean isWifiMeasurementRunning() {
        return this.wifiMeasurementRunning;
    }

    class MobileClient {
        private JSONArray nearbyStations;
        private JSONArray nearbyBeacons;
        private boolean currentLocationRequested;

        private Map<String, Region> beaconRegions = new HashMap<>();

        @JavascriptInterface
        public String getNearbyStations() {
            if (this.nearbyStations != null) {
                return this.nearbyStations.toString();
            } else {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getNearbyBeacons() {
            if (this.nearbyStations != null) {
                return this.nearbyBeacons.toString();
            } else {
                return "[]";
            }
        }

        @JavascriptInterface
        public void registerBeaconUuid(String uuid) {
            if (beaconRegions.containsKey(uuid)) {
                return;
            }
            checkLocationPermission(true);
            Region region = new Region(uuid, null, null, null);
            beaconRegions.put(uuid, region);
            runOnUiThread(() -> beaconManager.startRangingBeacons(region));
        }

        @JavascriptInterface
        public void unregisterBeaconUuid(String uuid) {
            Region region = beaconRegions.get(uuid);
            if (region != null) {
                beaconRegions.remove(uuid);
                runOnUiThread(() -> beaconManager.stopRangingBeacons(region));
            }
        }

        public void setNearbyStations(JSONArray nearbyStations) {
            this.nearbyStations = nearbyStations;
        }

        public void setNearbyBeacons(JSONArray nearbyBeacons) {
            this.nearbyBeacons = nearbyBeacons;
        }

        @JavascriptInterface
        public int getAppVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public void scanNow() {
            startScan();
        }

        @JavascriptInterface
        public boolean hasLocationPermission() {
            return MainActivity.this.hasLocationPermission();
        }

        @JavascriptInterface
        public boolean checkLocationPermission(boolean requestPermission) {
            return MainActivity.this.checkLocationPermission(requestPermission);
        }

        @JavascriptInterface
        public boolean checkLocationPermission() {
            return MainActivity.this.checkLocationPermission();
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

            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(getApplicationContext())) {
                Toast.makeText(MainActivity.this, R.string.shortcut_not_supported, Toast.LENGTH_LONG).show();
            }

            final ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(getApplicationContext(), url)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(IconCompat.createWithResource(getApplicationContext(), R.mipmap.ic_launcher_36c3))
                    .setIntent(shortcutIntent)
                    .build();

            ShortcutManagerCompat.requestPinShortcut(getApplicationContext(), shortcutInfo, null);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Toast.makeText(MainActivity.this, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
            }

        }

        @JavascriptInterface
        public void setUserData(String data) {
            Log.d("setUserData", data);

            final JSONObject user_data;
            try {
                user_data = new JSONObject(data);
            } catch (JSONException e) {
                Log.e("c3nav", "invalid JSON in setUserData: " + data, e);
                return;
            }

            cachedUserPermissions.updateFromUserData(user_data);

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        loggedIn = user_data.getBoolean("logged_in");
                    } catch (JSONException e) {
                        Log.e("c3navUserData", "missing required key logged_in in user data json object", e);
                        return;
                    }

                    if (loggedIn) {
                        navHeaderTitle.setText(user_data.optString("title"));
                        navHeaderTitle.setTypeface(null, Typeface.NORMAL);
                        navHeaderSubtitle.setText(user_data.isNull("subtitle") ? "" : user_data.optString("subtitle"));
                        accountLink.setTitle(R.string.your_account);
                    } else {
                        navHeaderTitle.setText(R.string.not_logged_in);
                        navHeaderTitle.setTypeface(null, Typeface.ITALIC);
                        navHeaderSubtitle.setText("");
                        accountLink.setTitle(R.string.login);
                    }

                    editorLink.setVisible(user_data.optBoolean("allow_editor"));
                    controlPanelLink.setVisible(user_data.optBoolean("allow_control_panel"));

                    hasChangeSet = user_data.optBoolean("has_changeset");
                    editorChangesLink.setEnabled(hasChangeSet);

                    boolean directEditing = user_data.optBoolean("direct_editing");
                    String changesCountDisplay = user_data.optString("changes_count_display");
                    SpannableString changesCountDisplaySpann = new SpannableString(user_data.optString("changes_count_display"));
                    if (directEditing)
                        changesCountDisplaySpann.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getApplicationContext(), R.color.colorWarning)), 0, changesCountDisplay.length(), 0);
                    editorChangesLink.setTitle(changesCountDisplaySpann);
                    editorChangesLink.setIcon(directEditing ? R.drawable.ic_assignment_turned_in : R.drawable.ic_assignment);

                    editorDashboardLink.setVisible(loggedIn);
                    navigationMenu.setGroupVisible(R.id.editorNav, !changesCountDisplay.isEmpty());
                    MainActivity.this.setInEditor(!changesCountDisplay.isEmpty());


                }
            });

        }

        @JavascriptInterface
        public void wificollectorStart() {
            Log.d("c3nav", "wificollector started");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setWifiMeasurementRunning(true);
                }
            });
        }

        @JavascriptInterface
        public void wificollectorStop() {
            Log.d("c3nav", "wificollector stopped");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setWifiMeasurementRunning(false);
                }
            });
        }

        public void setCurrentLocationRequested(boolean currentLocationRequested) {
            this.currentLocationRequested = currentLocationRequested;
        }

        @JavascriptInterface
        public boolean isCurrentLocationRequested() {
            if (this.currentLocationRequested) {
                this.currentLocationRequested = false;
                return true;
            }
            return false;
        }

        @JavascriptInterface
        public void currentLocationRequesteFailed() {
            Toast.makeText(MainActivity.this, R.string.current_location_request_failed, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public int getWifiScanRate() {
            return Integer.parseInt(sharedPrefs.getString(getString(R.string.wifi_scan_rate_key), "30"));
        }

        @JavascriptInterface
        public void suggestedWifiPeersReceived(String receivedPeersJson) {
            Log.i("c3nav", "Received suggested ranging peers: " + receivedPeersJson);
            try {
                JSONArray receivedPeers = new JSONArray(receivedPeersJson);
                MainActivity.this.suggestedWifiPeersReceived(receivedPeers);
            } catch (JSONException ex) {
                Log.w("c3nav", "failed to parse suggested wifi peers", ex);
            }
        }
    }

    private void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, resultCallback);
        } else {
            webView.loadUrl("javascript:" + script);
        }
    }

    private void evaluateJavascript(String script) {
        evaluateJavascript(script, null);
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return false;
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        } else if (itemId == R.id.share) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            i.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
            startActivity(Intent.createChooser(i, getString(R.string.share)));
            return true;
        } else if (itemId == R.id.refresh) {
            webView.loadUrl(instanceBaseUrl.toString());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static class SuggestedWifiPeer {
        public final String bssid;
        public final List<Integer> frequencies;

        SuggestedWifiPeer(String bssid, List<Integer> frequencies) {
            this.bssid = bssid;
            this.frequencies = frequencies;
        }
    }

    public void processWifiScanResults(List<ScanResult> results) {
        lastWifiScanResults.clear();
        for (ScanResult scanRes : results) {
            lastWifiScanResults.put(scanRes.BSSID, scanRes);
        }
        Log.d("c3nav", String.format("Nearby total ap count: %d", lastWifiScanResults.size()));
        pushWifiResultsToApp();
    }

    private boolean isRanging;

    private void startWifiRanging() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return;

        // lock to only have one ranging request at a time.
        // Sometimes ranging takes ~4s instead of ~1-2s and we don't want to start another one
        if (isRanging)
            return;
        isRanging = true;

        List<ResponderConfig> rangingPeers = new ArrayList<>();
        List<ScanResult> feasibleScannedPeers = lastWifiScanResults.values()
                .stream()
                .filter(ScanResult::is80211mcResponder)
                // nearer aps with higher rssi are more likely to successfully range
                .sorted(Comparator.comparingInt(a -> -a.level))
                .collect(Collectors.toList());

        final int maxRangingPeers = 10;
        final int maxSuggestions = 10;
        List<String> includedMacs = new ArrayList<>();

        for (ScanResult scanResult : feasibleScannedPeers) {
            rangingPeers.add(constructRangingPeerConfig(scanResult.BSSID, scanResult.frequency));
            includedMacs.add(scanResult.BSSID);
            Log.d("rtt", String.format("rtt-capable access point (scanned): %s", scanResult.BSSID));
            if (rangingPeers.size() >= maxRangingPeers)
                break;
        }

        int usedSuggestions = 0;
        for (SuggestedWifiPeer suggestedPeer : suggestedWifiPeers) {
            if (includedMacs.contains(suggestedPeer.bssid))
                continue;

            rangingPeers.add(constructRangingPeerConfig(suggestedPeer.bssid, suggestedPeer.frequencies.get(0)));
            includedMacs.add(suggestedPeer.bssid);
            Log.d("rtt", String.format("rtt-capable access point (suggested): %s", suggestedPeer.bssid));

            usedSuggestions++;
            if (usedSuggestions > maxSuggestions)
                break;
        }

        if (rangingPeers.isEmpty()) {
            Log.d("rtt", "no aps found for wifi ranging");
            return;
        }
        Log.d("rtt", String.format("starting rtt ranging with %d peers", rangingPeers.size()));
        performWifiRangingScans(rangingPeers, new ArrayList<>());
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private ResponderConfig constructRangingPeerConfig(String bssid, int frequency) {
        return new ResponderConfig.Builder()
                .setMacAddress(MacAddress.fromString(bssid))
                .setResponderType(RESPONDER_AP)
                .set80211mcSupported(true)
                .setFrequencyMhz(frequency)
                .setPreamble(PREAMBLE_VHT)
                .build();
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void performWifiRangingScans(List<ResponderConfig> rangingPeers, List<RangingResult> rangingResults) {
        // only perform ranging if app is still in foreground. Background ranging is very limited and
        // will quickly result in lost access to ranging for the app.
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
            return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES}, 0);
            return;
        }

        WifiRttManager mgr = (WifiRttManager) this.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        try {
            if (mgr == null || !mgr.isAvailable())
                return;
        } catch (NullPointerException e) {
            return;
        }

        // Android only allows to range for a specific amount of peers.
        // the peers are batched and then the batches are ranged consecutively
        final int batchSize = RangingRequest.getMaxPeers();
        List<ResponderConfig> currentBatchPeers;
        if (rangingPeers.size() <= batchSize)
            currentBatchPeers = rangingPeers;
        else
            currentBatchPeers = rangingPeers.subList(0, batchSize);

        RangingRequest.Builder reqBuilder = new RangingRequest.Builder();
        reqBuilder.setRttBurstSize(16);
        reqBuilder.addResponders(currentBatchPeers);

        try {
            mgr.startRanging(reqBuilder.build(), getMainExecutor(), new RangingResultCallback() {
                @Override
                public void onRangingFailure(int code) {
                    Log.w("rtt", String.format("ranging failure: %d", code));
                    MainActivity.this.lastWifiRangingResults.clear();
                    isRanging = false;
                }

                @Override
                public void onRangingResults(@NonNull List<RangingResult> results) {
                    for (RangingResult result : results) {
                        boolean isSuccess = result.getStatus() == RangingResult.STATUS_SUCCESS;
                        Log.d("rtt", String.format("ranging %s: %s", isSuccess ? "success" : "failure", result));
                        // check that distance is positive and not more than 100 meters. otherwise it is likely invalid
                        if (isSuccess && result.getDistanceMm() > 0 && result.getDistanceMm() < 100000)
                            rangingResults.add(result);
                    }

                    if (rangingPeers.size() <= batchSize) {
                        // this was the last batch. Process results
                        processWifiRangingResults(rangingResults);
                    } else {
                        // not done, do next batch
                        performWifiRangingScans(rangingPeers.subList(batchSize, rangingPeers.size()), rangingResults);
                    }
                }
            });
        } catch (Exception ex) {
            Log.d("rtt", "failed to perform wifi ranging", ex);
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private void processWifiRangingResults(List<RangingResult> rangingResults) {
        isRanging = false;

        lastWifiRangingResults.clear();
        for (RangingResult result : rangingResults) {
            MacAddress mac = result.getMacAddress();
            if (mac != null && result.getStatus() == RangingResult.STATUS_SUCCESS) {
                lastWifiRangingResults.put(mac.toString(), result);
            }
        }

        Log.d("rtt", String.format("finished wifi ranging. %d/%d ranged successfully", lastWifiRangingResults.size(), rangingResults.size()));
        pushWifiResultsToApp();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private String parseArubaAPName(ScanResult.InformationElement infoElem) {
        ByteBuffer buffer = infoElem.getBytes();
        if (buffer.hasRemaining() && (buffer.get() == (byte) 0x00) && buffer.hasRemaining() && (buffer.get() == (byte) 0x0b) && buffer.hasRemaining() && (buffer.get() == (byte) 0x86)) {
            if (buffer.hasRemaining() && buffer.get() == (byte) 0x01 && buffer.hasRemaining() && buffer.get() == (byte) 0x03) {
                if (buffer.hasRemaining()) {
                    buffer.get();
                }
                StringBuilder sb = new StringBuilder();
                while (buffer.hasRemaining()) {
                    sb.append((char) buffer.get());
                }
                String name = sb.toString();
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        return null;
    }

    public void pushWifiResultsToApp() {
        Set<String> macAddresses = new HashSet<>();
        macAddresses.addAll(lastWifiScanResults.keySet());
        macAddresses.addAll(lastWifiRangingResults.keySet());

        JSONArray ja = new JSONArray();
        for (String mac : macAddresses) {
            JSONObject jo = new JSONObject();
            try {
                if (lastWifiScanResults.containsKey(mac) && lastWifiScanResults.get(mac) != null)
                    mergeJsonObject(jo, serializeWifiScanResult(Objects.requireNonNull(lastWifiScanResults.get(mac))));
                if (lastWifiRangingResults.containsKey(mac))
                    mergeJsonObject(jo, serializeWifiRangingResult(lastWifiRangingResults.get(mac)));

                ja.put(jo);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.d("wifi locate result", ja.toString());
        mobileClient.setNearbyStations(ja);

        webView.post(() -> MainActivity.this.evaluateJavascript("nearby_stations_available();"));
    }

    private void mergeJsonObject(JSONObject target, JSONObject source) throws JSONException {
        Iterator<String> it = source.keys();
        while (it.hasNext()) {
            String key = it.next();
            target.put(key, source.get(key));
        }
    }

    private JSONObject serializeWifiScanResult(ScanResult scan) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("bssid", scan.BSSID);
        jo.put("ssid", scan.SSID);
        jo.put("rssi", scan.level);
        jo.put("frequency", scan.frequency);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            jo.put("supports80211mc", scan.is80211mcResponder());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            List<ScanResult.InformationElement> infoElems = scan.getInformationElements();
            for (ScanResult.InformationElement infoElem : infoElems) {
                if (infoElem.getId() == 221) {
                    String name = this.parseArubaAPName(infoElem);
                    if (name != null)
                        jo.put("ap_name", name);
                }
            }
        }
        jo.put("last", SystemClock.elapsedRealtime() - scan.timestamp / 1000);
        return jo;
    }

    private JSONObject serializeWifiRangingResult(RangingResult rttResult) throws JSONException {
        JSONObject jo = new JSONObject();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return jo;

        if (rttResult.getMacAddress() != null)
            jo.put("bssid", rttResult.getMacAddress().toString());
        jo.put("frequency", rttResult.getMeasurementChannelFrequencyMHz());

        JSONObject rtt = new JSONObject();
        rtt.put("distance_mm", rttResult.getDistanceMm());
        rtt.put("distance_std_dev_mm", rttResult.getDistanceStdDevMm());
        rtt.put("num_attempted_measurements", rttResult.getNumAttemptedMeasurements());
        rtt.put("num_successful_measurements", rttResult.getNumSuccessfulMeasurements());
        rtt.put("ranging_timestamp_millis", rttResult.getRangingTimestampMillis());
        rtt.put("measurement_bandwidth", rttResult.getMeasurementBandwidth());
        jo.put("rtt", rtt);
        jo.put("last", SystemClock.elapsedRealtime() - rttResult.getRangingTimestampMillis());
        return jo;
    }

    public void suggestedWifiPeersReceived(JSONArray suggestedWifiPeersJson) throws JSONException {
        List<SuggestedWifiPeer> parsedPeers = new ArrayList<>();
        for (int i = 0; i < suggestedWifiPeersJson.length(); i++) {
            JSONObject obj = suggestedWifiPeersJson.getJSONObject(i);

            List<Integer> parsedFrequencies = new ArrayList<>();
            JSONArray frequencyArr = obj.getJSONArray("frequencies");
            for (int j = 0; j < frequencyArr.length(); j++)
                parsedFrequencies.add(frequencyArr.getInt(j));

            parsedPeers.add(new SuggestedWifiPeer(obj.getString("bssid"), parsedFrequencies));
        }

        this.suggestedWifiPeers = parsedPeers;
        Log.d("c3nav", String.format("received new suggested wifi peers. count: %d", parsedPeers.size()));
    }

    class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            if (!checkLocationPermission()) return;
            List<ScanResult> scanList = wifiManager.getScanResults();
            MainActivity.this.processWifiScanResults(scanList);
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
        setWindowFlags();
    }

    private void setWindowFlags() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            if ((settingKeepOnTop && !isInEditor()) || settingKeepScreenOn && isWifiMeasurementRunning()) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
        } else {
            setShowWhenLocked((settingKeepOnTop && !isInEditor()) || settingKeepScreenOn && isWifiMeasurementRunning());
        }

        if (settingKeepScreenOn && isWifiMeasurementRunning()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public Intent getShortcutIntet(@NonNull String action, Uri data) {
        Intent shortcutIntent = new Intent(getApplicationContext(), MainActivity.class);
        shortcutIntent.setAction(action);
        if (data != null) shortcutIntent.setData(data);
        return shortcutIntent;
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    public ShortcutInfo getShortcutInfo(@NonNull String id, @NonNull String shortLabel, String longLabel, @NonNull Icon icon, @NonNull String action, Uri data) {
        ShortcutInfo.Builder shortcutInfoBuilder = new ShortcutInfo.Builder(getApplicationContext(), action)
                .setShortLabel(shortLabel)
                .setIcon(icon)
                .setIntent(getShortcutIntet(action, null));
        if (longLabel != null) shortcutInfoBuilder.setLongLabel(longLabel);
        return shortcutInfoBuilder.build();
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    public ShortcutInfo getShortcutInfo(@NonNull String id, int shortLabelRessource, int longLabelRessource, int iconRessource, @NonNull String action, Uri data) {
        String shortLabel = getString(shortLabelRessource);
        String longLabel = (longLabelRessource != -1) ? getString(longLabelRessource) : null;
        Icon icon = Icon.createWithResource(getApplicationContext(), iconRessource);
        return getShortcutInfo(id, shortLabel, longLabel, icon, action, data);
    }

    public void updateDynamicShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;

        final String CONTROL_PANEL_SHORTCUT_ID = "controlPanel";
        final String EDITOR_SHORTCUT_ID = "editor";
        final String SHORTCUT_PROGRAM_VERSION_KEY = "shortcutsLastUpdatedByVersion";

        boolean updateForced = false;

        ShortcutManager shortcutManager = getApplicationContext().getSystemService(ShortcutManager.class);

        List<ShortcutInfo> installedDynamicShortcuts = shortcutManager.getDynamicShortcuts();
        Set<String> installedDynamicShortcutsIDs = new HashSet<String>();
        Map<String, ShortcutInfo> currentDynamicShortcuts = new HashMap<String, ShortcutInfo>();

        for (ShortcutInfo shortcutInfo : installedDynamicShortcuts) {
            installedDynamicShortcutsIDs.add(shortcutInfo.getId());
        }

        // if shortcuts have been last updated by a different version recreate all, otherwise only changed.
        if (BuildConfig.VERSION_CODE != sharedPrefs.getInt(SHORTCUT_PROGRAM_VERSION_KEY, -1)) {
            Log.d("c3nav-shortcuts", "Program version changed, forcing update");
            updateForced = true;
        }

        if (cachedUserPermissions.hasControlPanelPermission()) {
            ShortcutInfo shortcutInfo = getShortcutInfo(CONTROL_PANEL_SHORTCUT_ID, R.string.shortcut_control_panel_short, R.string.shortcut_control_panel_long, R.drawable.ic_shortcut_build, ACTION_CONTROL_PANEL, null);
            currentDynamicShortcuts.put(shortcutInfo.getId(), shortcutInfo);
        }

        if (cachedUserPermissions.hasEditorPermission()) {
            ShortcutInfo shortcutInfo = getShortcutInfo(EDITOR_SHORTCUT_ID, R.string.shortcut_editor_short, R.string.shortcut_editor_long, R.drawable.ic_shortcut_edit, ACTION_EDITOR, null);
            currentDynamicShortcuts.put(shortcutInfo.getId(), shortcutInfo);
        }

        for (ShortcutInfo shortcutInfo : installedDynamicShortcuts) {
            if (currentDynamicShortcuts.containsKey(shortcutInfo.getId()) && !currentDynamicShortcuts.get(shortcutInfo.getId()).getIntent().getAction().equals(shortcutInfo.getIntent().getAction())) {
                Log.d("c3nav-shortcuts", "An Intend of an shortcut changed. forcing update");
                Log.d("c3nav-shortcuts", "new:" + currentDynamicShortcuts.get(shortcutInfo.getId()).getIntent().getAction() + " old:" + shortcutInfo.getIntent().getAction());
                updateForced = true;
                break;
            }
        }

        if (updateForced || !installedDynamicShortcutsIDs.equals(currentDynamicShortcuts.keySet())) {
            Log.d("c3nav-shortcuts", "DynamicShortcuts need update, updating...");
            if (currentDynamicShortcuts.isEmpty()) {
                shortcutManager.removeAllDynamicShortcuts();
            } else {
                shortcutManager.setDynamicShortcuts(new ArrayList<ShortcutInfo>(currentDynamicShortcuts.values()));
            }
            sharedPrefs.edit().putInt(SHORTCUT_PROGRAM_VERSION_KEY, BuildConfig.VERSION_CODE).apply();
        }

    }


    class CachedUserPermissions {
        private boolean allowControlPanel = false;
        private boolean allowEditor = false;
        private boolean loggedIn = false;

        public final static String KEY_ALLOW_CONTROL_PANEL = "cachedUserPermissionControlPanel";
        public final static String KEY_ALLOW_EDITOR = "cachedUserPermissionEditor";
        public final static String KEY_LOGGED_IN = "cachedUserPermissionLoggedIn";

        CachedUserPermissions() {
            super();
            allowControlPanel = sharedPrefs.getBoolean(KEY_ALLOW_CONTROL_PANEL, false);
            allowEditor = sharedPrefs.getBoolean(KEY_ALLOW_EDITOR, false);
            loggedIn = sharedPrefs.getBoolean(KEY_LOGGED_IN, false);
        }

        public void updateFromUserData(JSONObject userData) {
            boolean allowControlPanelOld = this.allowControlPanel;
            boolean allowEditorOld = this.allowEditor;
            boolean loggedInOld = this.loggedIn;

            try {
                allowControlPanel = userData.getBoolean("allow_control_panel");
                allowEditor = userData.getBoolean("allow_editor");
                loggedIn = userData.getBoolean("logged_in");
            } catch (JSONException e) {
                Log.e("c3navUserData", "failed to parse user data json object", e);
            }

            if (allowControlPanel != allowControlPanelOld || allowEditor != allowEditorOld || loggedIn != loggedInOld) {
                sharedPrefs.edit()
                        .putBoolean(KEY_ALLOW_CONTROL_PANEL, allowControlPanel)
                        .putBoolean(KEY_ALLOW_EDITOR, allowEditor)
                        .putBoolean(KEY_LOGGED_IN, loggedIn)
                        .apply();
                updateDynamicShortcuts();
            }
        }

        public boolean hasControlPanelPermission() {
            return allowControlPanel;
        }

        public boolean hasEditorPermission() {
            return allowEditor;
        }

        public boolean isLoggedIn() {
            return loggedIn;
        }
    }
}
