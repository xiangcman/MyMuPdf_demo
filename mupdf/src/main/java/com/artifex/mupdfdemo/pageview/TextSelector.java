package com.artifex.mupdfdemo.pageview;

import android.graphics.RectF;
import android.util.Log;
import com.artifex.mupdfdemo.TextWord;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

class TextSelector {

    final private TextWord[][] mText;
    final private RectF mSelectBox;

    public TextSelector(TextWord[][] text, RectF selectBox) {
        mText = text;
        mSelectBox = selectBox;
    }

    public void select(TextProcessor tp) {
        if (mText == null || mSelectBox == null) return;

        ArrayList<TextWord[]> lines = new ArrayList<TextWord[]>();
        for (TextWord[] line : mText)
            if (line[0].bottom > mSelectBox.top && line[0].top < mSelectBox.bottom) lines.add(line);

        Iterator<TextWord[]> it = lines.iterator();
        int lineIndex = 0;
        while (it.hasNext()) {
            TextWord[] line = it.next();
            boolean firstLine = line[0].top < mSelectBox.top;//如果每一行的第一个文字的上面坐标小于选中的top
            boolean lastLine = line[0].bottom > mSelectBox.bottom;
            float start = Float.NEGATIVE_INFINITY;
            float end = Float.POSITIVE_INFINITY;

            if (firstLine && lastLine) {
                start = Math.min(mSelectBox.left, mSelectBox.right);
                end = Math.max(mSelectBox.left, mSelectBox.right);
            } else if (firstLine) {
                start = mSelectBox.left;
            } else if (lastLine) {
                end = mSelectBox.right;
            }

            tp.onStartLine();
            Log.d("PageView", "firstLine:" + firstLine + ";lastLine:" + lastLine);

            for (TextWord word : line) {//遍历选中的行
                Log.d("PageView", word.w + ":" + word.left+";"+word.top);
                if (lineIndex > 0 && lineIndex < lines.size() - 1) {

                    tp.onWord(word);
                } else if (lineIndex == 0) {
                    if (lines.size() > 1) {
                        if (word.left > start) tp.onWord(word);
                    } else {
                        if (word.left > start && word.right < end) tp.onWord(word);
                    }
                } else {//最后一行
                    if (word.right < end) {
                        tp.onWord(word);
                    }
                }
                //if (word.right > start && word.left < end) {//只要右边大于起点就行
                //    tp.onWord(word);
                //}
            }
            //if (!firstLine && !lastLine) {//中间的文字
            //    Log.d("PageView", "中间行的文字:" + word.w);
            //    tp.onWord(word);
            //} else {
            //    Log.d("text", "wrod:" + word.w);
            //    if (word.right > start && word.left < end) {//只要右边大于起点就行
            //        tp.onWord(word);
            //    }
            //}

            tp.onEndLine(lineIndex, lines.size());
            lineIndex++;
        }
    }
}
