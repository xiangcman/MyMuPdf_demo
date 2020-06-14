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

/* This enum should be kept in line with the cooresponding C enum in mupdf.c */
public enum SignatureState {
    NoSupport,
    Unsigned,
    Signed
}

