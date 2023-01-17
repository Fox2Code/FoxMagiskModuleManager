package com.fox2code.mmm.utils.realm;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Required;

@SuppressWarnings("unused")
public class ReposList extends RealmObject {
    // Each repo is identified by its id, has a url field, and an enabled field
    // there's also an optional donate and support field

    @Required
    private String id;
    @Required
    private String url;
    private boolean enabled;
    private String donate;
    private String support;
    private String submitModule;
    private int lastUpdate;
    private String website;
    private String name;

    public ReposList(String id, String url, boolean enabled, String donate, String support) {
        this.id = id;
        this.url = url;
        this.enabled = enabled;
        this.donate = donate;
        this.support = support;
        this.submitModule = null;
        this.lastUpdate = 0;
    }

    public ReposList() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDonate() {
        return donate;
    }

    public void setDonate(String donate) {
        this.donate = donate;
    }

    public String getSupport() {
        return support;
    }

    public void setSupport(String support) {
        this.support = support;
    }

    // get metadata for a repo
    public static ReposList getRepo(String id) {
        Realm realm = Realm.getDefaultInstance();
        ReposList repo = realm.where(ReposList.class).equalTo("id", id).findFirst();
        realm.close();
        return repo;
    }

    public String getSubmitModule() {
        return submitModule;
    }

    public void setSubmitModule(String submitModule) {
        this.submitModule = submitModule;
    }

    public int getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(int lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }
}
