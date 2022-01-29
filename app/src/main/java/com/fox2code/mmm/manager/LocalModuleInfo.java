package com.fox2code.mmm.manager;

import android.util.Log;

import com.fox2code.mmm.utils.FastException;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.PropUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class LocalModuleInfo extends ModuleInfo {
    public String updateVersion;
    public long updateVersionCode = Long.MIN_VALUE;
    public String updateZipUrl;
    public String updateChangeLog;
    public String updateChecksum;

    public LocalModuleInfo(String id) {
        super(id);
    }

    public void checkModuleUpdate() {
        if (this.updateJson != null) {
            try {
                JSONObject jsonUpdate = new JSONObject(new String(Http.doHttpGet(
                        this.updateJson, false), StandardCharsets.UTF_8));
                this.updateVersion = jsonUpdate.optString("version");
                this.updateVersionCode = jsonUpdate.getLong("versionCode");
                this.updateZipUrl = jsonUpdate.getString("zipUrl");
                this.updateChangeLog = jsonUpdate.optString("changelog");
                this.updateChecksum = jsonUpdate.optString("checksum");
                if (this.updateZipUrl.isEmpty()) throw FastException.INSTANCE;
                this.updateVersion = PropUtils.shortenVersionName(
                        this.updateVersion.trim(), this.updateVersionCode);
            } catch (Exception e) {
                this.updateVersion = null;
                this.updateVersionCode = Long.MIN_VALUE;
                this.updateZipUrl = null;
                this.updateChangeLog = null;
                this.updateChecksum = null;
                Log.w("LocalModuleInfo",
                        "Failed update checking for module: " + this.id, e);
            }
        }
    }
}
