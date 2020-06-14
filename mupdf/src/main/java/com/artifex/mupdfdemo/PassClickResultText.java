package com.artifex.mupdfdemo;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.artifex.mupdfdemo.pageview.PageView;

import java.util.ArrayList;

public class PassClickResultText extends PassClickResult {
    public final String text;

    public PassClickResultText(boolean _changed, String _text) {
        super(_changed);
        text = _text;
    }

    public void acceptVisitor(PassClickResultVisitor visitor) {
        visitor.visitText(this);
    }
}

