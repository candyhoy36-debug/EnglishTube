package com.joy.englishtube.ui.main;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.joy.englishtube.R;

/**
 * Sprint 0 placeholder. Sprint 1 will replace this with WebView + nav bar
 * that loads m.youtube.com and routes /watch?v=... to PlayerActivity.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView versionText = findViewById(R.id.tv_version);
        versionText.setText(getString(R.string.skeleton_version_label));
    }
}
