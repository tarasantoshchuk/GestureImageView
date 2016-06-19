package com.tarasantoshchuk.gesture_image_view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

public class GestureImageView extends ImageView {
    private static final String TAG = GestureImageView.class.getSimpleName();

    private static final float DEGREE_HALF = 180.0f;

    private static final int GESTURE_NONE = -1;
    private static final int GESTURE_CLICK = 0;
    private static final int GESTURE_ROTATE = 1;
    private static final int GESTURE_TRANSLATE = 2;
    private static final int GESTURE_DOUBLE_CLICK = 3;

    private static final float TRANSLATE_START_SCALE = 1.05f;
    private static final float TRANSLATE_END_SCALE = 1 / TRANSLATE_START_SCALE;

    private static final int CLICK_DELAY = 200;

    private int mTouchSlop;
    private int mLongClickTime;

    private int mWidth;
    private int mHeight;

    private int mPreviousMotionEventX;
    private int mPreviousMotionEventY;

    private int mCurrentMotionEventX;
    private int mCurrentMotionEventY;

    private int mDrawableHeight;
    private int mDrawableWidth;

    private long mPreviousGestureEndTime;

    private Runnable mClickRunnable = new Runnable() {
        @Override
        public void run() {
            onClick();
        }
    };

    private Matrix mMatrix;

    private int mCurrentGesture;

    public GestureImageView(Context context) {
        this(context, null);
    }

    public GestureImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);

        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        mTouchSlop = viewConfiguration.getScaledTouchSlop() / 4;
        mLongClickTime = ViewConfiguration.getLongPressTimeout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Log.v(TAG, "onSizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;

        setInitialMatrix();
    }

    private void setInitialMatrix() {
        float scaleX = (float)mWidth / (float) mDrawableWidth;
        float scaleY = (float)mHeight / (float)mDrawableHeight;

        float scale = scaleX < scaleY ? scaleX : scaleY;

        mMatrix = new Matrix();

        if(scale < 1.0f) {
            mMatrix.postScale(scaleX, scaleY, mWidth / 2, mHeight / 2);
        }

        mMatrix.postTranslate((mWidth - mDrawableWidth) / 2, (mHeight - mDrawableHeight) / 2);
        setImageMatrix(mMatrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int eventAction = event.getAction();

        mPreviousMotionEventX = mCurrentMotionEventX;
        mPreviousMotionEventY = mCurrentMotionEventY;
        mCurrentMotionEventX = (int) event.getX();
        mCurrentMotionEventY = (int) event.getY();

        if(eventAction == MotionEvent.ACTION_DOWN) {
            //by default assume that click gesture started

            if(mCurrentGesture == GESTURE_CLICK && event.getEventTime() - mPreviousGestureEndTime < CLICK_DELAY) {
                removeCallbacks(mClickRunnable);
                mCurrentGesture = GESTURE_DOUBLE_CLICK;
            } else {
                mCurrentGesture = GESTURE_CLICK;
            }

            return true;
        }



        switch(eventAction) {
            case MotionEvent.ACTION_UP:
                mPreviousGestureEndTime = event.getEventTime();
                handleActionUp();
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                mCurrentGesture = GESTURE_NONE;
                return true;
            default:
                return false;
        }
    }

    private void onDoubleClick() {
        setInitialMatrix();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);

        mDrawableHeight = drawable.getIntrinsicHeight();
        mDrawableWidth = drawable.getIntrinsicWidth();

        Log.v(TAG, "setImageDrawable");
    }

    private void handleActionMove(MotionEvent event) {
        int deltaX = mCurrentMotionEventX - mPreviousMotionEventX;
        int deltaY = mCurrentMotionEventY - mPreviousMotionEventY;

        switch(mCurrentGesture) {
            case GESTURE_CLICK:
                if (isSwipeMoveAction(deltaX, deltaY)) {
                    mCurrentGesture = GESTURE_ROTATE;
                } else if(event.getEventTime() - event.getDownTime() > mLongClickTime) {
                    mCurrentGesture = GESTURE_TRANSLATE;
                    mMatrix.postScale(TRANSLATE_START_SCALE, TRANSLATE_START_SCALE, event.getX(), event.getY());
                    setImageMatrix(mMatrix);
                }
                break;
            case GESTURE_ROTATE:
                rotate();
                break;
            case GESTURE_TRANSLATE:
                translate(deltaX, deltaY);
                break;
        }
    }

    private void translate(int deltaX, int deltaY) {
        mMatrix.postTranslate(deltaX, deltaY);
        setImageMatrix(mMatrix);
    }

    private void rotate() {
        float deltaDegree = getCurrentDegree() - getPreviousDegree();

        float scale = getScale();

        Log.v(TAG, "rotate, delta degree " + deltaDegree);

        mMatrix.postRotate(deltaDegree, mWidth / 2, mHeight / 2);
        mMatrix.postScale(scale, scale, mWidth / 2, mHeight / 2);
        setImageMatrix(mMatrix);
    }

    private float getCurrentDegree() {
        return getDegreeFromCenter(mCurrentMotionEventX, mCurrentMotionEventY);
    }

    private float getPreviousDegree() {
        return getDegreeFromCenter(mPreviousMotionEventX, mPreviousMotionEventY);
    }

    private float getDegreeFromCenter(int x, int y) {
        double deltaX = x - mWidth / 2;
        double deltaY = y - mHeight / 2;

        Log.v(TAG, "getDegree, deltaX " + deltaX + ", deltaY " + deltaY);

        float result;

        if(deltaY == 0) {
            if(deltaX > 0) {
                result = -90;
            } else {
                result = 90;
            }
        } else {
            result = (float) (Math.atan(-deltaX / deltaY) * 180 / Math.PI);
            if(deltaY < 0) {
                result += 180;
            }
        }

        Log.v(TAG, "getDegree, result " + result);
        return result;
    }


    private boolean isSwipeMoveAction(int deltaX, int deltaY) {
        return Math.abs(deltaX) >= mTouchSlop || Math.abs(deltaY) >= mTouchSlop;
    }

    private void handleActionUp() {
        switch (mCurrentGesture) {
            case GESTURE_CLICK:
                postDelayed(mClickRunnable, CLICK_DELAY);
                break;
            case GESTURE_TRANSLATE:
                mMatrix.postScale(TRANSLATE_END_SCALE, TRANSLATE_END_SCALE, mCurrentMotionEventX, mCurrentMotionEventY);
                setImageMatrix(mMatrix);
                break;
            case GESTURE_DOUBLE_CLICK:
                onDoubleClick();
                break;
        }
    }

    private void onClick() {
        mMatrix.postRotate(DEGREE_HALF, mWidth / 2, mHeight / 2);
        setImageMatrix(mMatrix);
    }

    public float getScale() {
        double prevDistance = Math.sqrt(Math.pow(mPreviousMotionEventX - mWidth / 2, 2f) + Math.pow(mPreviousMotionEventY - mHeight / 2, 2f));
        double currentDistance = Math.sqrt(Math.pow(mCurrentMotionEventX - mWidth / 2, 2f) + Math.pow(mCurrentMotionEventY - mHeight / 2, 2f));

        return (float) (currentDistance / prevDistance);
    }
}
