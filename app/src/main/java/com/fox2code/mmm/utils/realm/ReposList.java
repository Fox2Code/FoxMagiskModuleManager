package com.fox2code.mmm.utils.realm;

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

    public ReposList(String id, String url, boolean enabled, String donate, String support) {
        this.id = id;
        this.url = url;
        this.enabled = enabled;
        this.donate = donate;
        this.support = support;
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
}
