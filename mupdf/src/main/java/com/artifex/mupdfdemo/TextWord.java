package com.artifex.mupdfdemo;

import android.graphics.RectF;
import android.util.Log;

public class TextWord extends RectF {

    public String w;

    public TextWord() {
        super();
        w = new String();
    }

    public void Add(TextChar tc) {
        Log.d("TextWord", "tc:" + tc.c);
        super.union(tc);
        w=String.valueOf(tc.c);
        //w = w.concat(new String(new char[]{tc.c}));
    }
}
