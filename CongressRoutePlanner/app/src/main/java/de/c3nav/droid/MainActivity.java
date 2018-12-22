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
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.ParseException;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.transition.AutoTransition;
import android.support.transition.Scene;
import android.support.transition.Slide;
import android.support.transition.Transition;
import android.support.transition.TransitionManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.WindowManager;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    //Request Codes
    public static final int PERM_REQUEST = 1;
    public final static int SETTINGS_REQUEST = 2;

    private WifiManager wifiManager;
    private PowerManager powerManager;
    private DrawerLayout mDrawerLayout;
    private NavigationView navigationView;
    private Menu navigationMenu;
    private Toolbar toolbar;
    private WebView webView;
    private MobileClient mobileClient;
    private Map<String, Integer> lastLevelValues = new HashMap<>();
    private boolean locationPermissionRequested;
    private Boolean locationPermissionCache = null;
    private WifiReceiver wifiReceiver;
    protected CustomSwipeToRefresh swipeLayout;

    private LinearLayout splashScreen;
    private VideoView logoAnimView;
    private LinearLayout logoScreen;
    private TextView logoScreenMessage;
    private EditText authUsername;
    private EditText authPassword;
    private TextView authMessage;
    private Button authLoginButton;

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
    private boolean settingUseWifiLocating = true;
    private boolean settingDeveloperModeEnabled = false;
    private String settingDeveloperInstanceUrl = "";
    private String settingDeveloperHttpUser = null;
    private String settingDeveloperHttpPassword = null;

    protected Uri instanceBaseUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        boolean activityStartedFromURLFiler = Intent.ACTION_VIEW.equals(intent.getAction())
                && intentCategories != null && (
                        intentCategories.contains(Intent.CATEGORY_DEFAULT) ||
                        intentCategories.contains(Intent.CATEGORY_BROWSABLE) );

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        locationPermissionRequested = sharedPrefs.getBoolean(getString(R.string.location_permission_requested_key), false);
        if (BuildConfig.DEBUG) {
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
        updateSettings();

        mDrawerLayout = findViewById(R.id.drawer_layout);

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem item) {
                        Intent browserIntent;
                        mDrawerLayout.closeDrawers();
                        switch (item.getItemId()) {
                            case R.id.accountLink:
                                Uri uri;
                                if(loggedIn) {
                                    uri=MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/account/").build();
                                } else {
                                    uri=MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/login")
                                            .appendQueryParameter("next", Uri.parse(webView.getUrl()).getPath()).build();
                                }
                                MainActivity.this.evaluateJavascript("window.openInModal ? openInModal('" + uri.toString() + "') : window.location='" + uri.toString() + "';");
                                return true;
                            case R.id.editorChangesLink:
                                webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/editor/changeset/").build().toString());
                                return true;
                            case R.id.editorDashboardLink:
                                webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/editor/user/").build().toString());
                                return true;
                            case R.id.mapLink:
                                webView.loadUrl(BuildConfig.WEB_URL);
                                return true;
                            case R.id.editorLink:
                                webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/editor/").build().toString());
                                return true;
                            case R.id.controlPanelLink:
                                webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/control/").build().toString());
                                return true;
                            case R.id.apiLink:
                                browserIntent = new Intent(Intent.ACTION_VIEW, MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/api/").build());
                                startActivity(browserIntent);
                                return true;
                            case R.id.twitterLink:
                                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/c3nav/"));
                                startActivity(browserIntent);
                                return true;
                            case R.id.githubLink:
                                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/c3nav/"));
                                startActivity(browserIntent);
                                return true;
                            case R.id.aboutLink:
                                webView.loadUrl(MainActivity.this.instanceBaseUrl.buildUpon().encodedPath("/about/").build().toString());
                                return true;
                            case R.id.settingsButton:
                                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                                startActivityForResult(settingsIntent, SETTINGS_REQUEST);
                            default:
                                return false;
                        }
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


        if (activityStartedFromLauncher) {
            mDrawerLayout.closeDrawers();
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
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
                maybeHideLoginScreen();
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

        String url_to_call = instanceBaseUrl.toString();
        Uri data = intent.getData();
        if (data != null) {
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

        hasLocationPermission(); //initialize locationPermissionCache
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
    }

    protected void updateSettings() {
        settingKeepOnTop = sharedPrefs.getBoolean(getString(R.string.keep_on_top_key), true);
        settingKeepScreenOn = sharedPrefs.getBoolean(getString(R.string.keep_screen_on_key), true);
        settingUseWifiLocating = sharedPrefs.getBoolean(getString(R.string.use_wifi_locating_key), true);

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
                if(!splashScreenDone) skipSplash();
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
            public void onTransitionStart(Transition transition) { }

            @Override
            public void onTransitionEnd(Transition transition) {
                // remove animated logo because the stuff in the background is still visible
                fadeoutSplashScreen();
            }

            @Override
            public void onTransitionCancel(Transition transition) {}

            @Override
            public void onTransitionPause(Transition transition) { }

            @Override
            public void onTransitionResume(Transition transition) { }
        });

        TransitionManager.go(new Scene((ViewGroup) splashScreen.getParent()), mySwapTransition);
        splashScreen.setGravity(Gravity.TOP|Gravity.CENTER);
        logoAnimView.getLayoutParams().height = (int) toolbar.getHeight();
        logoAnimView.requestLayout();
    }

    protected void fadeoutSplashScreen() {
        if (splashScreenFadeoutStarted) return;
        splashScreenFadeoutStarted = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // center of the clipping circle
            int cx = (int) swipeLayout.getWidth()/2;
            int cy = (int) toolbar.getHeight()/2;

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
        logoAnimView.setVideoPath("");
    }

    protected void skipSplash() {
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        splashScreenDone = true;
        splashScreen.setVisibility(View.GONE);
        unloadSplashVideo();
    }

    protected void showLogoScreen() {
        if (!splashScreenDone) {
            skipSplash();
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
        TransitionManager.go(new Scene((ViewGroup) logoScreen.getParent()), new Slide(Gravity.LEFT));
        logoScreen.setVisibility(View.GONE);
        logoScreenMessage.setVisibility(View.GONE);
        authUsername.setVisibility(View.GONE);
        authPassword.setVisibility(View.GONE);
        authMessage.setVisibility(View.GONE);
        authLoginButton.setVisibility(View.GONE);
        loginScreenIsActive = false;
        logoScreenIsVisible = false;
    }

    protected void maybeShowLoginScreen() {
        if (httpAuthNeeded && (splashScreenDone || logoAnimFinished)) {
            if (loginScreenIsActive) {
                showLoginScreen("ERROR");
            } else {
                showLoginScreen();
            }
        }
    }

    protected void maybeHideLoginScreen() {
        if (loginScreenIsActive && initialPageLoaded) {
            hideLoginScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("lifecycleEvents", "onResume called");
        evaluateJavascript("if (mobileclientOnResume) {mobileclientOnResume()};");
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        if(checkLocationPermission(false, true)) startScan();
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
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionCache = Boolean.TRUE;
                    if (!settingUseWifiLocating) {
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putBoolean(getString(R.string.use_wifi_locating_key), true);
                        editor.commit();
                        settingUseWifiLocating = true;
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
        editor.commit();
    }

    protected boolean checkLocationPermission(boolean requestPermission, boolean ignoreCache) {
        if (!settingUseWifiLocating && !requestPermission) return false;
        if (ignoreCache || locationPermissionCache == null) {
            int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            locationPermissionCache = new Boolean(permissionCheck == PackageManager.PERMISSION_GRANTED);
        }

        if (!locationPermissionCache.booleanValue()) {
            if (requestPermission) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERM_REQUEST);
                this.setLocationPermissionRequested(true);
            }
            return false;
        }
        return settingUseWifiLocating;
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
        if (!settingUseWifiLocating) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (!powerManager.isScreenOn()) return;
        } else {
            if (!powerManager.isInteractive()) return;
        }
        if (!hasLocationPermission()) return;
        Log.d("c3navWifiScanner", "startScan triggered");
        wifiManager.startScan();
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

        @JavascriptInterface
        public void setUserData(String data) {
            Log.d("setUserData", data);

            final JSONObject user_data;
            try {
                user_data = new JSONObject(data);
            } catch (JSONException e) {
                Log.d("c3nav", "invalid JSON in setUserData: " + data);
                return;
            }

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        loggedIn = user_data.getBoolean("logged_in");
                    } catch (JSONException e) {
                        return;
                    }

                    if (loggedIn) {
                        navHeaderTitle.setText(user_data.optString("title"));
                        navHeaderTitle.setTypeface(null, Typeface.NORMAL);
                        navHeaderSubtitle.setText(user_data.optString("subtitle"));
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

                    String changesCountDisplay = user_data.optString("changes_count_display");
                    editorChangesLink.setTitle(changesCountDisplay);
                    editorDashboardLink.setVisible(loggedIn);
                    navigationMenu.setGroupVisible(R.id.editorNav, !changesCountDisplay.isEmpty());
                    MainActivity.this.setInEditor(!changesCountDisplay.isEmpty());

                    boolean directEditing = user_data.optBoolean("direct_editing");
                    editorChangesLink.setIcon(directEditing ? R.drawable.ic_assignment_turned_in : R.drawable.ic_assignment);

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
    }

    private void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, resultCallback);
        } else {
            webView.loadUrl("javascript:"+script);
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
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
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

            if (!checkLocationPermission()) return;

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

            webView.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.evaluateJavascript("nearby_stations_available();");
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
        setWindowFlags();
    }

    private void setWindowFlags() {
        if ((settingKeepOnTop && !isInEditor()) || settingKeepScreenOn && isWifiMeasurementRunning()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        if(settingKeepScreenOn && isWifiMeasurementRunning()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

}
