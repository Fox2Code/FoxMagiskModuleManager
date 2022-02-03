package com.fox2code.mmm.manager;

import android.util.Log;

import com.fox2code.mmm.utils.FastException;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.PropUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.noties.markwon.Markwon;

public class LocalModuleInfo extends ModuleInfo {
    public String updateVersion;
    public long updateVersionCode = Long.MIN_VALUE;
    public String updateZipUrl;
    public String updateChangeLogUrl;
    public String updateChangeLog = "";
    public String updateChecksum;

    public LocalModuleInfo(String id) {
        super(id);
    }

    public void checkModuleUpdate() {
        if (this.updateJson != null) {
            try {
                JSONObject jsonUpdate = new JSONObject(new String(Http.doHttpGet(
                        this.updateJson, true), StandardCharsets.UTF_8));
                this.updateVersion = jsonUpdate.optString("version");
                this.updateVersionCode = jsonUpdate.getLong("versionCode");
                this.updateZipUrl = jsonUpdate.getString("zipUrl");
                this.updateChangeLogUrl = jsonUpdate.optString("changelog");
                try {
                    String desc = new String(Http.doHttpGet(
                            this.updateChangeLogUrl, true), StandardCharsets.UTF_8);
                    if (desc.length() > 1000) {
                        desc = desc.substring(0, 1000);
                    }
                    this.updateChangeLog = desc;
                } catch (IOException ioe) {
                    this.updateChangeLog = "";
                }
                this.updateChecksum = jsonUpdate.optString("checksum");
                if (this.updateZipUrl.isEmpty()) throw FastException.INSTANCE;
                this.updateVersion = PropUtils.shortenVersionName(
                        this.updateVersion.trim(), this.updateVersionCode);
                if (this.updateChangeLog.length() > 1000)
                    this.updateChangeLog = this.updateChangeLog.substring(0, 1000);
            } catch (Exception e) {
                this.updateVersion = null;
                this.updateVersionCode = Long.MIN_VALUE;
                this.updateZipUrl = null;
                this.updateChangeLog = "";
                this.updateChecksum = null;
                Log.w("LocalModuleInfo",
                        "Failed update checking for module: " + this.id, e);
            }
        }
    }
}
