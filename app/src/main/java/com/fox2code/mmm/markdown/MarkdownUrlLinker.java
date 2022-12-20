package com.fox2code.mmm.markdown;

import android.util.Log;

import com.fox2code.mmm.BuildConfig;

import java.util.ArrayList;

public class MarkdownUrlLinker {
    private static final String TAG = "MarkdownUrlLinker";

    public static String urlLinkify(String url) {
        int index = url.indexOf("https://");
        if (index == -1) return url;
        ArrayList<LinkifyTask> linkifyTasks = new ArrayList<>();
        int extra = 0;
        while (index != -1) {
            int end = url.indexOf(' ', index);
            end = end == -1 ? url.indexOf('\n', index) :
                    Math.min(url.indexOf('\n', index), end);
            if (end == -1) end = url.length();
            if (index == 0 ||
                    '\n' == url.charAt(index - 1) ||
                    ' ' == url.charAt(index - 1)) {
                int endDomain = url.indexOf('/', index + 9);
                char endCh = url.charAt(end - 1);
                if (endDomain != -1 && endDomain < end &&
                        endCh != '>' && endCh != ')' && endCh != ']') {
                    linkifyTasks.add(new LinkifyTask(index, end));
                    extra += (end - index) + 4;
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Linkify url: " + url.substring(index, end));
                    }
                }
            }
            index = url.indexOf("https://", end);
        }
        if (linkifyTasks.isEmpty()) return url;
        LinkifyTask prev = LinkifyTask.NULL;
        StringBuilder stringBuilder = new StringBuilder(url.length() + extra);
        for (LinkifyTask linkifyTask : linkifyTasks) {
            stringBuilder.append(url, prev.end, linkifyTask.start)
                    .append('[').append(url, linkifyTask.start, linkifyTask.end)
                    .append("](").append(url, linkifyTask.start, linkifyTask.end).append(')');
            prev = linkifyTask;
        }
        if (prev.end != url.length()) stringBuilder.append(url, prev.end, url.length());
        Log.i(TAG, "Added Markdown link to " + linkifyTasks.size() + " urls");
        return stringBuilder.toString();
    }

    private static class LinkifyTask {
        static final LinkifyTask NULL = new LinkifyTask(0, 0);

        private final int start;
        private final int end;

        private LinkifyTask(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
