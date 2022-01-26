package com.fox2code.mmm.deeplink;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.settings.SettingsActivity;

/**
 * Note: Code must be under high security standard, as any website can do a request to this
 */
public class DeepLinkActivity extends CompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = this.getIntent();
        if (intent == null) {
            this.forceBackPressed();
            return;
        }
        Uri uri = intent.getData();
        String path;
        if (uri == null || (path = uri.getPath()) == null || uri.getHost() == null ||
                !uri.getHost().equals("https".equals(uri.getScheme()) ?
                        "mmm.fox2code.com" : BuildConfig.APPLICATION_ID)) {
            this.forceBackPressed();
            return;
        }
        while (path.startsWith("/"))
            path = path.substring(1);
        if (path.isEmpty() || path.equals("home")) {
            this.startActivity(MainActivity.class);
            return;
        }
        if (path.equals("settings")) {
            this.startActivity(SettingsActivity.class);
            return;
        }
        this.forceBackPressed();
    }

    private void startActivity(Class<? extends Activity> activity) {
        this.startActivityForResult(new Intent(this, activity).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK),
                (resultCode, data) -> this.forceBackPressed());
    }
}
