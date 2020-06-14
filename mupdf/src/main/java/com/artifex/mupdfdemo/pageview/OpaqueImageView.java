package com.artifex.mupdfdemo.pageview;

import android.content.Context;
import androidx.appcompat.widget.AppCompatImageView;

// Make our ImageViews opaque to optimize redraw
public class OpaqueImageView extends AppCompatImageView {

    public OpaqueImageView(Context context) {
        super(context);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }
}