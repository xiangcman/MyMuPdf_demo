package com.artifex.mupdfdemo;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Handler;

import com.lonelypluto.pdflibrary.R;

public class ProgressDialogX extends ProgressDialog {
    ProgressDialogX(Context context) {
        super(context);
    }

    private boolean mCancelled = false;

    boolean isCancelled() {
        return !mCancelled;
    }

    @Override
    public void cancel() {
        mCancelled = true;
        super.cancel();
    }
}

