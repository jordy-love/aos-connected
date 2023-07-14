package kr.co.itforone.connected;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import kr.co.itforone.connected.databinding.ActivityMainBinding;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding biding;

    public WebView webView;
    public CookieManager cookieManager;

    private long backPrssedTime = 0;

    final int FILECHOOSER_NORMAL_REQ_CODE = 1200, FILECHOOSER_LOLLIPOP_REQ_CODE = 1300;
    public ValueCallback<Uri> filePathCallbackNormal;
    public ValueCallback<Uri[]> filePathCallbackLollipop;
    public Uri mCapturedImageURI;
    public String loadUrl = "";

    // public static String TOKEN = ""; // 푸시토큰
    public boolean pushOrSharedPage = false; // 푸시알림&공유하기로 진입했는지 여부

    public SharedPreferences preferences;  // 로그인데이터저장
    public SharedPreferences.Editor pEditor;

    private ActivityManager am = ActivityManager.getInstance();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 데이터바인딩
        biding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        biding.setMainData(this);

        am.addActivity(this);
        webView = biding.webview;

        // setTOKEN(); // 파이어베이스 토큰

        // 쿠키매니저
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(this);
        }
        setCookieAllow(cookieManager, webView);

        // 로그인데이터저장
        preferences = getSharedPreferences("member", Activity.MODE_PRIVATE);
        if (preferences != null) {
            pEditor = preferences.edit();
        }

        // splash start
        Intent splash = new Intent(this, SplashActivity.class);
        startActivity(splash);

        webView.addJavascriptInterface(new WebviewJavainterface(this), "Android");
        webView.setWebViewClient(new ClientManager(this));
        webView.setWebChromeClient(new ChoromeManager(this, this));
        webView.setWebContentsDebuggingEnabled(true); // 크롬디버깅
        WebSettings settings = webView.getSettings();
        settings.setUserAgentString(settings.getUserAgentString() + "AConnected/APP_VER=1.0");
        settings.setTextZoom(100);
        settings.setJavaScriptEnabled(true);    // 자바스크립트
        // 휴대폰본인인증시 필수설정
        settings.setDomStorageEnabled(true);    //  로컬스토리지 사용
        settings.setJavaScriptCanOpenWindowsAutomatically(true); // window.open()허용

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {    // 뒤로가기 net::ERR_CACHE_MISS
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }*/
        settings.setAppCacheMaxSize(1024 * 1024 * 8); // 8mb
        File dir = getCacheDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        settings.setAppCachePath(dir.getPath());
        settings.setAllowFileAccess(true);
        settings.setAppCacheEnabled(true);  // 앱내부캐시사용여부

        // 웹뷰url 설정
        loadUrl = getString(R.string.index);

        // push&공유하기 url체크
        try {
            Intent intent = getIntent();

            if (intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri uriData = intent.getData();
                //Log.d("로그:uriData", uriData.toString());
                if (uriData != null) {
                    String idx = uriData.getQueryParameter("idx");
                    if (!idx.equals("")) {
                        loadUrl = uriData.getQueryParameter("url").toString() + "?idx=" + uriData.getQueryParameter("idx").toString();
                        pushOrSharedPage = true;
                    }
                }
            } else if (!intent.getExtras().getString("goUrl").equals("")) {
                loadUrl = intent.getExtras().getString("goUrl");
                pushOrSharedPage = true;
            }

        } catch (Exception e) {
            Log.d("로그:uriData_exc", e.toString());
        }

        // 로그인데이터 get전달
        loadUrl += (loadUrl.contains("?")) ? "&" : "?";
        loadUrl += "app_mb_id=" + preferences.getString("appLoginId", "");
        Log.d("로그:onCreate", loadUrl);

        webView.loadUrl(loadUrl);
        //webView.clearCache(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().stopSync();
        }
        Log.d("로그-onPause()", "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().startSync();
        }
        Log.d("로그-onResume()", "");
    }

    //뒤로가기이벤트
    @Override
    public void onBackPressed() {
        WebBackForwardList historyList = webView.copyBackForwardList();
        String currentUrl = webView.getUrl();

        if (currentUrl.contains(getString(R.string.index)) || currentUrl.endsWith(getString(R.string.domain))) {
            long tempTime = System.currentTimeMillis();
            long intervalTime = tempTime - backPrssedTime;

            if (0 <= intervalTime && 2000 >= intervalTime) {
                am.finishAllActivity();
            } else {
                backPrssedTime = tempTime;
                Toast.makeText(getApplicationContext(), "한번 더 뒤로가기 누를시 앱이 종료됩니다.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (webView.canGoBack()) {
            String backTargetUrl = historyList.getItemAtIndex(historyList.getCurrentIndex() - 1).getUrl();
            Log.d("로그:currentUrl", currentUrl);
            Log.d("로그:backTargetUrl", backTargetUrl);

            if (currentUrl.contains("/bbs/login.php") || currentUrl.contains("/bbs/setting.php") || currentUrl.contains("/bbs/mygroup.php")) {
                webView.clearHistory();
                webView.loadUrl(getString(R.string.index));
            }

            webView.goBack();

        } else {
            long tempTime = System.currentTimeMillis();
            long intervalTime = tempTime - backPrssedTime;

            if (0 <= intervalTime && 2000 >= intervalTime) {
                am.finishAllActivity();

            } else {
                if (pushOrSharedPage) { // 푸시or공유하기로 진입시 뒤로가기
                    pushOrSharedPage = false;
                    webView.clearHistory();
                    webView.loadUrl(getString(R.string.index));
                } else {
                    backPrssedTime = tempTime;
                    Toast.makeText(getApplicationContext(), "한번 더 뒤로가기 누를시 앱이 종료됩니다.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void setCookieAllow(CookieManager cookieManager, WebView webView) {
        try {
            cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                cookieManager.setAcceptThirdPartyCookies(webView, true);
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("로그-onActivityResult()", "");

        if (resultCode == RESULT_OK) {
            if (requestCode == FILECHOOSER_NORMAL_REQ_CODE) {
                if (filePathCallbackNormal == null) return;
                Uri result = (data == null || resultCode != RESULT_OK) ? null : data.getData();
                filePathCallbackNormal.onReceiveValue(result);
                filePathCallbackNormal = null;

            } else if (requestCode == FILECHOOSER_LOLLIPOP_REQ_CODE) {
                Uri[] result = new Uri[0];
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // 카메라/갤러리 선택
                    if (resultCode == RESULT_OK) {
                        result = (data == null) ? new Uri[]{mCapturedImageURI} : WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                    }
                    filePathCallbackLollipop.onReceiveValue(result);
                    filePathCallbackLollipop = null;
                }
            }
        } else {
            try {
                if (filePathCallbackLollipop != null) {
                    filePathCallbackLollipop.onReceiveValue(null);
                    filePathCallbackLollipop = null;
                    //webView.loadUrl("javascript:removeInputFile()");
                }
            } catch (Exception e) {
            }
        }
    }

    // 토큰저장
    // public static void setTOKEN() {
    //     FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
    //         @Override
    //         public void onComplete(@NonNull Task<String> task) {
    //             if (!task.isSuccessful()) {
    //                 return;
    //             }
    //             TOKEN = task.getResult();
    //             Log.d("로그:TOKEN", TOKEN);
    //         }
    //     });
    // }
}