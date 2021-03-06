package com.foodscan.Activity;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;

import com.foodscan.R;
import com.foodscan.Utility.UserDefaults;

public class PrivacyPolicyActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = PrivacyPolicyActivity.class.getSimpleName();

    private Context mContext;

    private WebView webView;
    private ImageView img_back;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        overridePendingTransition(R.anim.slide_right, R.anim.translate);

        mContext = PrivacyPolicyActivity.this;

        initView();
        initGlobals();
    }

    private void initView() {

        webView = findViewById(R.id.webview);
        img_back = findViewById(R.id.img_back);

    }

    private void initGlobals() {

        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(UserDefaults.PRIVACY_POLICY);

        img_back.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.img_back: {
                onBackPressed();
            }
            break;

        }
    }
}
