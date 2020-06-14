package com.artifex.mupdfdemo.pageview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.artifex.mupdfdemo.Annotation;
import com.artifex.mupdfdemo.CancellableAsyncTask;
import com.artifex.mupdfdemo.CancellableTaskDefinition;
import com.artifex.mupdfdemo.LinkInfo;
import com.artifex.mupdfdemo.MuPDFReaderView;
import com.artifex.mupdfdemo.ReaderView;
import com.artifex.mupdfdemo.TextWord;
import com.lonelypluto.pdflibrary.R;
import com.lonelypluto.pdflibrary.utils.SharedPreferencesUtil;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class PageView extends ViewGroup {

    private static final float ITEM_SELECT_BOX_WIDTH = 4.0f;// 选中时边框的宽

    private static final int HIGHLIGHT_COLOR = 0x80ff5722;// 选中文字时的颜色
    private int LINK_COLOR = 0x80ff5722;// 超链接颜色
    //    private static final int BOX_COLOR = 0xFF4444FF;
    private static final int BOX_COLOR = 0xFF696969;// 选中时边框的颜色
    //    private static final int INK_COLOR = 0xFF000000;// 绘制时画笔颜色
    private int INK_COLOR = 0xFF000000;// 绘制时画笔颜色
    //    private static final float INK_THICKNESS = 10.0f;// 绘制时画笔宽
    private float INK_THICKNESS = 10.0f;// 绘制时画笔宽
    private float current_scale;

    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_DIALOG_DELAY = 200;
    protected final Context mContext;
    protected int mPageNumber;
    private Point mParentSize;
    protected Point mSize;   // Size of page at minimum zoom
    protected float mSourceScale;

    private ImageView mEntire; // Image rendered at minimum zoom
    private Bitmap mEntireBm;
    private Matrix mEntireMat;
    private AsyncTask<Void, Void, TextWord[][]> mGetText;
    private AsyncTask<Void, Void, LinkInfo[]> mGetLinkInfo;
    private CancellableAsyncTask<Void, Void> mDrawEntire;

    private Point mPatchViewSize; // View size on the basis of which the patch was created
    private Rect mPatchArea;
    private ImageView mPatch;
    private Bitmap mPatchBm;
    private CancellableAsyncTask<Void, Void> mDrawPatch;
    private RectF mSearchBoxes[];
    protected LinkInfo mLinks[];
    private RectF mSelectBox;
    private RectF mPressBox;
    private TextWord mText[][];
    private RectF mItemSelectBox;
    protected ArrayList<ArrayList<PointF>> mDrawing;
    private View mSearchView;
    private boolean mIsBlank;
    private boolean mHighlightLinks;// 是否高亮显示超链接

    private ProgressBar mBusyIndicator;
    private final Handler mHandler = new Handler();

    private Bitmap bitmapIconLeft;
    private Bitmap bitmapIconRight;

    public PageView(Context c, Point parentSize, Bitmap sharedHqBm) {
        super(c);
        mContext = c;
        mParentSize = parentSize;
        setBackgroundColor(BACKGROUND_COLOR);
        mEntireBm = Bitmap.createBitmap(parentSize.x, parentSize.y, Config.ARGB_8888);
        mPatchBm = sharedHqBm;
        mEntireMat = new Matrix();
        bitmapIconLeft = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.iv_book_left_icon);
        bitmapIconRight = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.iv_book_right_icon);
    }

    protected abstract CancellableTaskDefinition<Void, Void> getDrawPageTask(Bitmap bm, int sizeX, int sizeY,
            int patchX, int patchY, int patchWidth, int patchHeight);

    protected abstract CancellableTaskDefinition<Void, Void> getUpdatePageTask(Bitmap bm, int sizeX, int sizeY,
            int patchX, int patchY, int patchWidth, int patchHeight);

    protected abstract LinkInfo[] getLinkInfo();

    protected abstract TextWord[][] getText();

    protected abstract void addMarkup(PointF[] quadPoints, Annotation.Type type);

    private void reinit() {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancelAndWait();
            mDrawEntire = null;
        }

        if (mDrawPatch != null) {
            mDrawPatch.cancelAndWait();
            mDrawPatch = null;
        }

        if (mGetLinkInfo != null) {
            mGetLinkInfo.cancel(true);
            mGetLinkInfo = null;
        }

        if (mGetText != null) {
            mGetText.cancel(true);
            mGetText = null;
        }

        mIsBlank = true;
        mPageNumber = 0;

        if (mSize == null) mSize = mParentSize;

        if (mEntire != null) {
            mEntire.setImageBitmap(null);
            mEntire.invalidate();
        }

        if (mPatch != null) {
            mPatch.setImageBitmap(null);
            mPatch.invalidate();
        }

        mPatchViewSize = null;
        mPatchArea = null;

        mSearchBoxes = null;
        mLinks = null;
        mSelectBox = null;
        mText = null;
        mItemSelectBox = null;
    }

    public void releaseResources() {
        reinit();

        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
    }

    public void releaseBitmaps() {
        reinit();

        //  recycle bitmaps before releasing them.

        if (mEntireBm != null) mEntireBm.recycle();
        mEntireBm = null;

        if (mPatchBm != null) mPatchBm.recycle();
        mPatchBm = null;
    }

    public void blank(int page) {
        reinit();
        mPageNumber = page;

        if (mBusyIndicator == null) {
            mBusyIndicator = new ProgressBar(mContext);
            mBusyIndicator.setIndeterminate(true);
            addView(mBusyIndicator);
        }

        setBackgroundColor(BACKGROUND_COLOR);
    }

    public void setPage(int page, PointF size) {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancelAndWait();
            mDrawEntire = null;
        }

        mIsBlank = false;
        // Highlights may be missing because mIsBlank was true on last draw
        if (mSearchView != null) mSearchView.invalidate();

        mPageNumber = page;
        if (mEntire == null) {
            mEntire = new OpaqueImageView(mContext);
            mEntire.setScaleType(ImageView.ScaleType.MATRIX);
            addView(mEntire);
        }

        // Calculate scaled size that fits within the screen limits
        // This is the size at minimum zoom
        mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y);
        mSize = new Point((int) (size.x * mSourceScale), (int) (size.y * mSourceScale));

        mEntire.setImageBitmap(null);
        mEntire.invalidate();

        // Get the link info in the background
        mGetLinkInfo = new AsyncTask<Void, Void, LinkInfo[]>() {
            protected LinkInfo[] doInBackground(Void... v) {
                return getLinkInfo();
            }

            protected void onPostExecute(LinkInfo[] v) {
                mLinks = v;
                if (mSearchView != null) mSearchView.invalidate();
            }
        };

        mGetLinkInfo.execute();

        // Render the page in the background
        mDrawEntire = new CancellableAsyncTask<Void, Void>(
                getDrawPageTask(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y)) {

            @Override
            public void onPreExecute() {
                setBackgroundColor(BACKGROUND_COLOR);
                mEntire.setImageBitmap(null);
                mEntire.invalidate();

                if (mBusyIndicator == null) {
                    mBusyIndicator = new ProgressBar(mContext);
                    mBusyIndicator.setIndeterminate(true);
                    addView(mBusyIndicator);
                    mBusyIndicator.setVisibility(INVISIBLE);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            if (mBusyIndicator != null) mBusyIndicator.setVisibility(VISIBLE);
                        }
                    }, PROGRESS_DIALOG_DELAY);
                }
            }

            @Override
            public void onPostExecute(Void result) {
                removeView(mBusyIndicator);
                mBusyIndicator = null;
                mEntire.setImageBitmap(mEntireBm);
                mEntire.invalidate();
                setBackgroundColor(Color.TRANSPARENT);

            }
        };

        mDrawEntire.execute();

        if (mSearchView == null) {
            mSearchView = new View(mContext) {
                @Override
                protected void onDraw(final Canvas canvas) {
                    super.onDraw(canvas);
                    Log.d("ReaderView", "mSearchView onDraw");
                    // Work out current total scale factor
                    // from source to view
                    final float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
                    //final float scaleY = mSourceScale * (float) getHeight() / (float) mSize.y;
                    current_scale = scale;

                    final Paint paint = new Paint();

                    if (!mIsBlank && mSearchBoxes != null) {
                        // 搜索颜色
                        //                        paint.setColor(HIGHLIGHT_COLOR);
                        //                        paint.setColor(mContext.getResources().getColor(R.color.search_bg));
                        paint.setColor(SharedPreferencesUtil.getSearchTextColor());

                        for (RectF rect : mSearchBoxes)
                            canvas.drawRect(rect.left * scale, rect.top * scale, rect.right * scale,
                                    rect.bottom * scale, paint);

                        //canvas.drawRect(rect.left * scale, rect.top , rect.right * scale,
                        //            rect.bottom , paint);
                    }

                    if (!mIsBlank && mLinks != null && mHighlightLinks) {
                        // 超链接颜色
                        paint.setColor(LINK_COLOR);
                        //                        paint.setColor(mContext.getResources().getColor(R.color.link_bg));
                        for (LinkInfo link : mLinks)
                            canvas.drawRect(link.rect.left * scale, link.rect.top * scale, link.rect.right * scale,
                                    link.rect.bottom * scale, paint);
                    }
                    if (mPressBox != null && mText != null) {
                        //if (!rect.isEmpty())
                        ////canvas.drawRect(rect.left * scale, rect.top * scale, rect.right * scale, rect.bottom * scale, paint);
                        //{
                        paint.setColor(HIGHLIGHT_COLOR);
                        canvas.drawRect(mPressBox.left * scale, mPressBox.top * scale * 1.01f, mPressBox.right * scale,
                                mPressBox.top * scale * 1.01f + mPressBox.height() * scale / 1.5f, paint);

                        //canvas.drawRect(rect.left * scale, rect.top * scale + rect.height(), rect.right * scale,
                        //        rect.top * scale + rect.height()+rect.height()*scale, paint);
                        //}
                        //if (lineIndex == 0) {
                        //    bottom = rect.top * scale * 1.01f + rect.height() * scale / 1.5f;
                        //    left = rect.left * scale;
                        //}
                        //if (lineIndex == totalLines - 1) {
                        //    endBottom = rect.top * scale * 1.01f + rect.height() * scale / 1.5f;
                        //    endLeft = rect.right * scale;
                        //}

                        canvas.drawBitmap(bitmapIconLeft, mPressBox.left * scale - bitmapIconLeft.getWidth(),
                                mPressBox.top * scale * 1.01f + mPressBox.height() * scale / 1.5f, null);
                        canvas.drawBitmap(bitmapIconRight, mPressBox.right * scale,
                                mPressBox.top * scale * 1.01f + mPressBox.height() * scale / 1.5f, null);
                    }
                    if (mSelectBox != null && mText != null) {
                        // 选中文字 复制，高亮，下划线，删除线选中时的颜色
                        paint.setColor(HIGHLIGHT_COLOR);
                        //                        paint.setColor(SharedPreferencesUtil.getSearchTextColor());
                        processSelectedText(new TextProcessor() {
                            RectF rect;
                            private float bottom;
                            private float left;
                            private float endBottom;
                            private float endLeft;

                            public void onStartLine() {
                                rect = new RectF();
                            }

                            public void onWord(TextWord word) {
                                rect.union(word);
                            }

                            public void onEndLine(int lineIndex, int totalLines) {
                                Log.d("PageView", "scale:" + scale);
                                Log.d("PageView", "ect.height():" + rect.height());
                                //把rect的top和bottom的位置改变点
                                if (!rect.isEmpty())
                                //canvas.drawRect(rect.left * scale, rect.top * scale, rect.right * scale, rect.bottom * scale, paint);
                                {
                                    canvas.drawRect(rect.left * scale, rect.top * scale * 1.01f, rect.right * scale,
                                            rect.top * scale * 1.01f + rect.height() * scale / 1.5f, paint);

                                    //canvas.drawRect(rect.left * scale, rect.top * scale + rect.height(), rect.right * scale,
                                    //        rect.top * scale + rect.height()+rect.height()*scale, paint);
                                }
                                if (lineIndex == 0) {
                                    bottom = rect.top * scale * 1.01f + rect.height() * scale / 1.5f;
                                    left = rect.left * scale;
                                }
                                if (lineIndex == totalLines - 1) {
                                    endBottom = rect.top * scale * 1.01f + rect.height() * scale / 1.5f;
                                    endLeft = rect.right * scale;
                                }

                                canvas.drawBitmap(bitmapIconLeft, left - bitmapIconLeft.getWidth(), bottom, null);
                                canvas.drawBitmap(bitmapIconRight, endLeft, endBottom, null);
                            }
                        });
                    }

                    // 选中时的外边框
                    if (mItemSelectBox != null) {
                        paint.setStyle(Paint.Style.STROKE);
                        // 边框宽
                        paint.setStrokeWidth(ITEM_SELECT_BOX_WIDTH);
                        // 边框颜色
                        paint.setColor(BOX_COLOR);
                        //                        paint.setColor(mContext.getResources().getColor(R.color.link_bg));
                        canvas.drawRect(mItemSelectBox.left * scale, mItemSelectBox.top * scale,
                                mItemSelectBox.right * scale, mItemSelectBox.bottom * scale, paint);
                    }

                    if (mDrawing != null) {
                        Path path = new Path();
                        PointF p;

                        paint.setAntiAlias(true);
                        paint.setDither(true);
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        paint.setStrokeCap(Paint.Cap.ROUND);

                        paint.setStyle(Paint.Style.FILL);
                        // 绘制时画笔宽
                        paint.setStrokeWidth(INK_THICKNESS * scale);
                        //                        Logger.e("zyw", "StrokeWidth = " + INK_THICKNESS * scale);
                        // 绘制时画笔颜色
                        paint.setColor(INK_COLOR);
                        Iterator<ArrayList<PointF>> it = mDrawing.iterator();
                        while (it.hasNext()) {
                            ArrayList<PointF> arc = it.next();
                            if (arc.size() >= 2) {
                                Iterator<PointF> iit = arc.iterator();
                                p = iit.next();
                                float mX = p.x * scale;
                                float mY = p.y * scale;
                                path.moveTo(mX, mY);
                                while (iit.hasNext()) {
                                    p = iit.next();
                                    float x = p.x * scale;
                                    float y = p.y * scale;
                                    path.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
                                    mX = x;
                                    mY = y;
                                }
                                path.lineTo(mX, mY);
                            } else {
                                p = arc.get(0);
                                canvas.drawCircle(p.x * scale, p.y * scale, INK_THICKNESS * scale / 2, paint);
                            }
                        }

                        paint.setStyle(Paint.Style.STROKE);
                        canvas.drawPath(path, paint);
                    }
                }
            };

            addView(mSearchView);
        }
        requestLayout();
    }

    public void setSearchBoxes(RectF searchBoxes[]) {
        mSearchBoxes = searchBoxes;
        if (mSearchView != null) mSearchView.invalidate();
    }

    /**
     * 设置是否高亮显示超链接
     *
     * @param f boolean
     */
    public void setLinkHighlighting(boolean f) {
        mHighlightLinks = f;
        if (mSearchView != null) mSearchView.invalidate();
    }

    /**
     * 设置超链接颜色
     *
     * @param color 颜色值
     */
    public void setLinkHighlightColor(int color) {
        LINK_COLOR = color;
        if (mHighlightLinks) {
            if (mSearchView != null) {
                mSearchView.invalidate();
            }
        }
    }

    public void deselectText() {
        mSelectBox = null;
        mSearchView.invalidate();
    }

    /**
     * 长点击的时候选中文字
     */
    public void longPressText(MotionEvent e) {
        //默认选中三个文字
        float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
        final float docRelX0 = (e.getX() - getLeft()) / scale;
        final float docRelY0 = (e.getY() - getTop()) / scale;
        //寻找另外一个点，该点可能是左边的，也可能是右边的
        if (mGetText == null) {
            mGetText = new AsyncTask<Void, Void, TextWord[][]>() {
                @Override
                protected TextWord[][] doInBackground(Void... params) {
                    return getText();
                }

                @Override
                protected void onPostExecute(TextWord[][] result) {
                    //这里判断下
                    mText = result;
                    mPressBox = getOtherTextPosition(docRelX0, docRelY0);
                    mSearchView.invalidate();
                }
            };
            mGetText.execute();
        } else {
            mPressBox = getOtherTextPosition(docRelX0, docRelY0);
            mSearchView.invalidate();
        }
    }

    private RectF getOtherTextPosition(float docRelX0, float docRelY0) {
        if (mText != null) {
            RectF rectF = new RectF();
            for (TextWord[] textWords : mText) {
                for (int i = 0; i < textWords.length; i++) {
                    TextWord textWord = textWords[i];
                    if (textWord.contains(docRelX0, docRelY0)) {
                        Log.d("PageView", "选中文字:" + textWord.w);
                        if (i == textWords.length - 1) {//该行的最后一个文字，向前找两个
                            TextWord preWord;
                            if (i - 2 >= 0) {
                                preWord = textWords[i - 2];
                            } else if (i - 1 >= 0) {
                                preWord = textWords[i - 1];
                            } else {
                                //只能取自己了
                                preWord = textWord;
                            }
                            rectF.left = preWord.left;
                            rectF.top = preWord.top;
                            rectF.right = textWord.right;
                            rectF.bottom = textWord.bottom;
                            return rectF;
                        } else {
                            TextWord nextWord;
                            //向后找两个文字
                            if (i + 2 <= textWords.length - 1) {
                                nextWord = textWords[i + 2];
                            } else if (i + 1 <= textWords.length - 1) {
                                nextWord = textWords[i + 1];
                            } else {
                                nextWord = textWord;
                            }
                            rectF.left = textWord.left;
                            rectF.top = textWord.top;
                            rectF.right = nextWord.right;
                            rectF.bottom = nextWord.bottom;
                            return rectF;
                        }
                    }
                }
            }
        }
        return null;
    }

    public void selectText(float x0, float y0, float x1, float y1) {
        float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
        float docRelX0 = (x0 - getLeft()) / scale;
        float docRelY0 = (y0 - getTop()) / scale;
        float docRelX1 = (x1 - getLeft()) / scale;
        float docRelY1 = (y1 - getTop()) / scale;
        // Order on Y but maintain the point grouping
        if (docRelY0 <= docRelY1) {
            mSelectBox = new RectF(docRelX0, docRelY0, docRelX1, docRelY1);
        } else {
            mSelectBox = new RectF(docRelX1, docRelY1, docRelX0, docRelY0);
        }

        mSearchView.invalidate();
        //}

        if (mGetText == null) {
            mGetText = new AsyncTask<Void, Void, TextWord[][]>() {
                @Override
                protected TextWord[][] doInBackground(Void... params) {
                    return getText();
                }

                @Override
                protected void onPostExecute(TextWord[][] result) {
                    //这里判断下
                    mText = result;
                    mSearchView.invalidate();
                }
            };
            mGetText.execute();
        }
    }

    public void startDraw(float x, float y) {
        float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
        float docRelX = (x - getLeft()) / scale;
        float docRelY = (y - getTop()) / scale;
        if (mDrawing == null) mDrawing = new ArrayList<ArrayList<PointF>>();

        ArrayList<PointF> arc = new ArrayList<PointF>();
        arc.add(new PointF(docRelX, docRelY));
        mDrawing.add(arc);
        mSearchView.invalidate();
    }

    public void continueDraw(float x, float y) {
        float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
        float docRelX = (x - getLeft()) / scale;
        float docRelY = (y - getTop()) / scale;

        if (mDrawing != null && mDrawing.size() > 0) {
            ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
            arc.add(new PointF(docRelX, docRelY));
            mSearchView.invalidate();
        }
    }

    public void cancelDraw() {
        mDrawing = null;
        mSearchView.invalidate();
    }

    protected PointF[][] getDraw() {
        if (mDrawing == null) return null;

        PointF[][] path = new PointF[mDrawing.size()][];

        for (int i = 0; i < mDrawing.size(); i++) {
            ArrayList<PointF> arc = mDrawing.get(i);
            path[i] = arc.toArray(new PointF[arc.size()]);
        }

        return path;
    }

    /**
     * 设置画笔颜色
     *
     * @param color 颜色值
     */
    public void setInkColor(int color) {
        INK_COLOR = color;
    }

    /**
     * 设置画笔粗细
     *
     * @param inkThickness 粗细值
     */
    public void setPaintStrockWidth(float inkThickness) {
        INK_THICKNESS = inkThickness;
    }

    protected float getInkThickness() {
        if (current_scale == 0) {
            return 9.07563f / 2;
        } else {
            //            return (INK_THICKNESS * current_scale) / 2;
            return INK_THICKNESS / 2;
        }
    }

    public float getCurrentScale() {
        if (current_scale == 0) {
            return 9.07563f;
        }
        return current_scale;
    }

    protected float[] getColor() {

        return changeColor(INK_COLOR);
    }

    /**
     * 将十进制颜色值转换成RGB格式
     */
    private float[] changeColor(int color) {

        int red = (color & 0xff0000) >> 16;
        int green = (color & 0x00ff00) >> 8;
        int blue = (color & 0x0000ff);

        float colors[] = new float[3];
        colors[0] = red / 255f;
        colors[1] = green / 255f;
        colors[2] = blue / 255f;

        return colors;
    }

    protected void processSelectedText(TextProcessor tp) {
        (new TextSelector(mText, mSelectBox)).select(tp);
    }

    public void setItemSelectBox(RectF rect) {
        mItemSelectBox = rect;
        if (mSearchView != null) mSearchView.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int x, y;
        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                x = mSize.x;
                break;
            default:
                x = MeasureSpec.getSize(widthMeasureSpec);
        }
        switch (MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                y = mSize.y;
                break;
            default:
                y = MeasureSpec.getSize(heightMeasureSpec);
        }

        setMeasuredDimension(x, y);

        if (mBusyIndicator != null) {
            int limit = Math.min(mParentSize.x, mParentSize.y) / 2;
            mBusyIndicator.measure(MeasureSpec.AT_MOST | limit, MeasureSpec.AT_MOST | limit);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;

        if (mEntire != null) {
            if (mEntire.getWidth() != w || mEntire.getHeight() != h) {
                mEntireMat.setScale(w / (float) mSize.x, h / (float) mSize.y);
                mEntire.setImageMatrix(mEntireMat);
                mEntire.invalidate();
            }
            mEntire.layout(0, 0, w, h);
        }

        if (mSearchView != null) {
            mSearchView.layout(0, 0, w, h);
        }

        if (mPatchViewSize != null) {
            if (mPatchViewSize.x != w || mPatchViewSize.y != h) {
                // Zoomed since patch was created
                mPatchViewSize = null;
                mPatchArea = null;
                if (mPatch != null) {
                    mPatch.setImageBitmap(null);
                    mPatch.invalidate();
                }
            } else {
                mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
            }
        }

        if (mBusyIndicator != null) {
            int bw = mBusyIndicator.getMeasuredWidth();
            int bh = mBusyIndicator.getMeasuredHeight();

            mBusyIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }
    }

    public void updateHq(boolean update) {
        Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
        if (viewArea.width() == mSize.x || viewArea.height() == mSize.y) {
            // If the viewArea's size matches the unzoomed size, there is no need for an hq patch
            if (mPatch != null) {
                mPatch.setImageBitmap(null);
                mPatch.invalidate();
            }
        } else {
            final Point patchViewSize = new Point(viewArea.width(), viewArea.height());
            final Rect patchArea = new Rect(0, 0, mParentSize.x, mParentSize.y);

            // Intersect and test that there is an intersection
            if (!patchArea.intersect(viewArea)) return;

            // Offset patch area to be relative to the view top left
            patchArea.offset(-viewArea.left, -viewArea.top);

            boolean area_unchanged = patchArea.equals(mPatchArea) && patchViewSize.equals(mPatchViewSize);

            // If being asked for the same area as last time and not because of an update then nothing to do
            if (area_unchanged && !update) return;

            boolean completeRedraw = !(area_unchanged);

            // Stop the drawing of previous patch if still going
            if (mDrawPatch != null) {
                mDrawPatch.cancelAndWait();
                mDrawPatch = null;
            }

            // Create and add the image view if not already done
            if (mPatch == null) {
                mPatch = new OpaqueImageView(mContext);
                mPatch.setScaleType(ImageView.ScaleType.MATRIX);
                addView(mPatch);
                mSearchView.bringToFront();
            }

            CancellableTaskDefinition<Void, Void> task;

            if (completeRedraw) {
                task = getDrawPageTask(mPatchBm, patchViewSize.x, patchViewSize.y, patchArea.left, patchArea.top,
                        patchArea.width(), patchArea.height());
            } else {
                task = getUpdatePageTask(mPatchBm, patchViewSize.x, patchViewSize.y, patchArea.left, patchArea.top,
                        patchArea.width(), patchArea.height());
            }

            mDrawPatch = new CancellableAsyncTask<Void, Void>(task) {

                public void onPostExecute(Void result) {
                    mPatchViewSize = patchViewSize;
                    mPatchArea = patchArea;
                    mPatch.setImageBitmap(mPatchBm);
                    mPatch.invalidate();
                    //requestLayout();
                    // Calling requestLayout here doesn't lead to a later call to layout. No idea
                    // why, but apparently others have run into the problem.
                    mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
                }
            };

            mDrawPatch.execute();
        }
    }

    public void update() {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancelAndWait();
            mDrawEntire = null;
        }

        if (mDrawPatch != null) {
            mDrawPatch.cancelAndWait();
            mDrawPatch = null;
        }

        // Render the page in the background
        mDrawEntire = new CancellableAsyncTask<Void, Void>(
                getUpdatePageTask(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y)) {

            public void onPostExecute(Void result) {
                mEntire.setImageBitmap(mEntireBm);
                mEntire.invalidate();
            }
        };

        mDrawEntire.execute();

        updateHq(true);
    }

    public void removeHq() {
        // Stop the drawing of the patch if still going
        if (mDrawPatch != null) {
            mDrawPatch.cancelAndWait();
            mDrawPatch = null;
        }

        // And get rid of it
        mPatchViewSize = null;
        mPatchArea = null;
        if (mPatch != null) {
            mPatch.setImageBitmap(null);
            mPatch.invalidate();
        }
    }

    public int getPage() {
        return mPageNumber;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }
}
