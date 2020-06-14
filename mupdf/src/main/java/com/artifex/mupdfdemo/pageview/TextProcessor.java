package com.artifex.mupdfdemo.pageview;

import com.artifex.mupdfdemo.TextWord;

public interface TextProcessor {

    void onStartLine();

    void onWord(TextWord word);

    void onEndLine(int lineIndex, int totalLines);
}
