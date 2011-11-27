/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.ui;

import com.android.camera.PreferenceGroup;
import com.android.camera.R;
import com.android.camera.Util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.util.HashMap;
/**
 * A view that contains camera setting indicators in two levels. The first-level
 * indicators including the zoom, camera picker, flash and second-level control.
 * The second-level indicators are the merely for the camera settings.
 */
public class IndicatorControlWheel extends IndicatorControl implements
        View.OnClickListener {
    public static final int HIGHLIGHT_WIDTH = 4;

    private static final String TAG = "IndicatorControlWheel";
    private static final int HIGHLIGHT_DEGREES = 30;
    private static final double HIGHLIGHT_RADIANS = Math.toRadians(HIGHLIGHT_DEGREES);

    // The following angles are based in the zero degree on the right. Here we
    // have the fix 45 degrees for each sector in the first-level as we have to
    // align the zoom button exactly at degree 180. For second-level indicators,
    // the indicators located evenly between start and end angle. In addition,
    // these indicators for the second-level hidden in the same wheel with
    // larger angle values are visible after rotation.
    private static final int FIRST_LEVEL_END_DEGREES = 270;
    private static final int FIRST_LEVEL_SECTOR_DEGREES = 45;
    private static final int SECOND_LEVEL_START_DEGREES = 60;
    private static final int SECOND_LEVEL_END_DEGREES = 300;
    private static final int CLOSE_ICON_DEFAULT_DEGREES = 315;
    private static final int ZOOM_ICON_DEFAULT_DEGREES = 180;

    private static final int ANIMATION_TIME = 300; // milliseconds

    // The width of the edges on both sides of the wheel, which has less alpha.
    private static final float EDGE_STROKE_WIDTH = 6f;
    private static final int TIME_LAPSE_ARC_WIDTH = 6;

    private final int HIGHLIGHT_COLOR;
    private final int TIME_LAPSE_ARC_COLOR;

    // The center of the shutter button.
    private int mCenterX, mCenterY;
    // The width of the wheel stroke.
    private int mStrokeWidth;
    private double mShutterButtonRadius;
    private double mWheelRadius;
    private double mChildRadians[];
    private Paint mBackgroundPaint;
    private RectF mBackgroundRect;
    // The index of the child that is being pressed. -1 means no child is being
    // pressed.
    private int mPressedIndex = -1;

    // Time lapse recording variables.
    private int mTimeLapseInterval;  // in ms
    private long mRecordingStartTime = 0;
    private long mNumberOfFrames = 0;

    // Remember the last event for event cancelling if out of bound.
    private MotionEvent mLastMotionEvent;

    private ImageView mSecondLevelIcon;
    private ImageView mCloseIcon;

    // Variables for animation.
    private long mAnimationStartTime;
    private boolean mInAnimation = false;
    private Handler mHandler = new Handler();
    private final Runnable mRunnable = new Runnable() {
        public void run() {
            requestLayout();
        }
    };

    // Variables for level control.
    private int mCurrentLevel = 0;
    private int mSecondLevelStartIndex = -1;
    private double mStartVisibleRadians[] = new double[2];
    private double mEndVisibleRadians[] = new double[2];
    private double mSectorRadians[] = new double[2];
    private double mTouchSectorRadians[] = new double[2];

    private ImageView mZoomIcon;

    public IndicatorControlWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        HIGHLIGHT_COLOR = resources.getColor(R.color.review_control_pressed_color);
        TIME_LAPSE_ARC_COLOR = resources.getColor(R.color.time_lapse_arc);

        setWillNotDraw(false);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
        mBackgroundPaint.setAntiAlias(true);

        mBackgroundRect = new RectF();
    }

    private int getChildCountByLevel(int level) {
        // Get current child count by level.
        if (level == 1) {
            return (getChildCount() - mSecondLevelStartIndex);
        } else {
            return mSecondLevelStartIndex;
        }
    }

    @Override
    public void onClick(View view) {
        if (view == mZoomIcon) return;
        mPressedIndex = -1;
        dismissSettingPopup();
        mInAnimation = true;
        mAnimationStartTime = SystemClock.uptimeMillis();
        requestLayout();
    }

    public void initialize(Context context, PreferenceGroup group,
            boolean isZoomSupported, String[] keys, /*String[]*/HashMap otherSettingKeys) {
        mShutterButtonRadius = IndicatorControlWheelContainer.SHUTTER_BUTTON_RADIUS;
        mStrokeWidth = Util.dpToPixel(IndicatorControlWheelContainer.STROKE_WIDTH);
        mWheelRadius = mShutterButtonRadius + mStrokeWidth * 0.5;

        setPreferenceGroup(group);

        // Add Zoom Icon.
        if (isZoomSupported) {
            mZoomIcon = (ImageView) addImageButton(context, R.drawable.ic_zoom_holo_light, false);
        }

        // Add CameraPicker.
        initializeCameraPicker();

        // Add second-level Indicator Icon.
        mSecondLevelIcon = addImageButton(context, R.drawable.ic_settings_holo_light, true);
        mSecondLevelStartIndex = getChildCount();

        // Add second-level buttons.
        mCloseIcon = addImageButton(context, R.drawable.btn_wheel_close_settings, false);
        addControls(keys, otherSettingKeys); //Guru

        // The angle(in radians) of each icon for touch events.
        mChildRadians = new double[getChildCount()];
        presetFirstLevelChildRadians();
        presetSecondLevelChildRadians();
    }

    private ImageView addImageButton(Context context, int resourceId, boolean rotatable) {
        ImageView view;
        if (rotatable) {
            view = new RotateImageView(context);
        } else {
            view = new ColorFilterImageView(context);
        }
        view.setImageResource(resourceId);
        view.setOnClickListener(this);
        addView(view);
        return view;
    }

    private int getTouchIndicatorIndex(double delta) {
        // The delta is the angle of touch point in radians.
        if (mInAnimation) return -1;
        int count = getChildCountByLevel(mCurrentLevel);
        if (count == 0) return -1;
        int startIndex = (mCurrentLevel == 0) ? 0 : mSecondLevelStartIndex;
        int sectors = count - 1;
        // Check which indicator is touched.
        if ((delta >= (mStartVisibleRadians[mCurrentLevel] - HIGHLIGHT_RADIANS / 2)) &&
            (delta <= (mEndVisibleRadians[mCurrentLevel] + HIGHLIGHT_RADIANS / 2))) {
            int index = (int) ((delta - mStartVisibleRadians[mCurrentLevel])
                    / mSectorRadians[mCurrentLevel]);

            // greater than the center of ending indicator
            if (index > sectors) return (startIndex + sectors);
            // less than the center of starting indicator
            if (index < 0) return startIndex;

            if (delta <= (mChildRadians[startIndex + index]
                    + mTouchSectorRadians[mCurrentLevel] / 2)) {
                return (startIndex + index);
            }
            if (delta >= (mChildRadians[startIndex + index + 1]
                    - mTouchSectorRadians[mCurrentLevel] / 2)) {
                return (startIndex + index + 1);
            }
        }
        return -1;
    }

    private void injectMotionEvent(int viewIndex, MotionEvent event, int action) {
        View v = getChildAt(viewIndex);
        event.setAction(action);
        v.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;
        mLastMotionEvent = event;
        int action = event.getAction();

        double dx = event.getX() - mCenterX;
        double dy = mCenterY - event.getY();
        double radius = Math.sqrt(dx * dx + dy * dy);

        // Ignore the event if too far from the shutter button.
        if ((radius <= (mWheelRadius + mStrokeWidth)) && (radius > mShutterButtonRadius)) {
            double delta = Math.atan2(dy, dx);
            if (delta < 0) delta += Math.PI * 2;
            int index = getTouchIndicatorIndex(delta);
            // Move over from one indicator to another.
            if ((index != mPressedIndex) || (action == MotionEvent.ACTION_DOWN)) {
                if (mPressedIndex != -1) {
                    injectMotionEvent(mPressedIndex, event, MotionEvent.ACTION_CANCEL);
                } else {
                    // Cancel the popup if it is different from the selected.
                    if (getSelectedIndicatorIndex() != index) dismissSettingPopup();
                }
                if ((index != -1) && (action == MotionEvent.ACTION_MOVE)) {
                    injectMotionEvent(index, event, MotionEvent.ACTION_DOWN);
                }
            }
            if ((index != -1) && (action != MotionEvent.ACTION_MOVE)) {
                View view = getChildAt(index);
                // Switch to zoom control only if a touch down event is received.
                if ((view == mZoomIcon) && (action == MotionEvent.ACTION_DOWN)
                        && mZoomIcon.isEnabled()) {
                    mPressedIndex = -1;
                    mOnIndicatorEventListener.onIndicatorEvent(
                            OnIndicatorEventListener.EVENT_ENTER_ZOOM_CONTROL);
                    return true;
                } else {
                    getChildAt(index).dispatchTouchEvent(event);
                }
            }
            // Once the button is up, reset the press index.
            mPressedIndex = (action == MotionEvent.ACTION_UP) ? -1 : index;
            invalidate();
            return true;
        }
        // The event is not on any of the child.
        onTouchOutBound();
        return false;
    }

    private void rotateWheel() {
        int totalDegrees = CLOSE_ICON_DEFAULT_DEGREES - SECOND_LEVEL_START_DEGREES;
        int startAngle = ((mCurrentLevel == 0) ? CLOSE_ICON_DEFAULT_DEGREES
                                               : SECOND_LEVEL_START_DEGREES);
        if (mCurrentLevel == 0) totalDegrees = -totalDegrees;

        int elapsedTime = (int) (SystemClock.uptimeMillis() - mAnimationStartTime);
        if (elapsedTime >= ANIMATION_TIME) {
            elapsedTime = ANIMATION_TIME;
            mCurrentLevel = (mCurrentLevel == 0) ? 1 : 0;
            mInAnimation = false;
        }

        int expectedAngle = startAngle + (totalDegrees * elapsedTime / ANIMATION_TIME);
        double increment = Math.toRadians(expectedAngle)
                - mChildRadians[mSecondLevelStartIndex];
        for (int i = 0 ; i < getChildCount(); ++i) mChildRadians[i] += increment;
        requestLayout();
   }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (mInAnimation) {
            rotateWheel();
            mHandler.post(mRunnable);
        }
        mCenterX = right - left - Util.dpToPixel(
                IndicatorControlWheelContainer.FULL_WHEEL_RADIUS);
        mCenterY = (bottom - top) / 2;

        // Layout the indicators based on the current level.
        // The icons are spreaded on the left side of the shutter button.
        for (int i = 0; i < getChildCount(); ++i) {
            View view = getChildAt(i);
            if (!view.isEnabled()) continue;
            double radian = mChildRadians[i];
            double startVisibleRadians = mInAnimation
                    ? mStartVisibleRadians[1]
                    : mStartVisibleRadians[mCurrentLevel];
            double endVisibleRadians = mInAnimation
                    ? mEndVisibleRadians[1]
                    : mEndVisibleRadians[mCurrentLevel];
            if ((radian < (startVisibleRadians - HIGHLIGHT_RADIANS / 2)) ||
                    (radian > (endVisibleRadians + HIGHLIGHT_RADIANS / 2))) {
                view.setVisibility(View.GONE);
                continue;
            }
            view.setVisibility(View.VISIBLE);
            int x = mCenterX + (int)(mWheelRadius * Math.cos(radian));
            int y = mCenterY - (int)(mWheelRadius * Math.sin(radian));
            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            view.layout(x - width / 2, y - height / 2, x + width / 2,
                    y + height / 2);
        }
    }

    private void presetFirstLevelChildRadians() {
        int count = getChildCountByLevel(0);
        int sectors = (count <= 1) ? 0 : (count - 1);
        double sectorDegrees = FIRST_LEVEL_SECTOR_DEGREES;
        mSectorRadians[0] = Math.toRadians(sectorDegrees);
        int zoomIndex = indexOfChild(mZoomIcon);
        double degrees;

        // Make sure the zoom button is located at 180 degrees. If there are
        // more buttons than we could show in the visible angle from 90 degrees
        // to 270 degrees, the modification of FIRST_LEVEL_SECTOR_DEGREES is
        // required then.
        if (zoomIndex >= 0) {
            degrees = ZOOM_ICON_DEFAULT_DEGREES - (zoomIndex * sectorDegrees);
        }  else {
            degrees = FIRST_LEVEL_END_DEGREES - (sectors * sectorDegrees);
        }
        mStartVisibleRadians[0] = Math.toRadians(degrees);

        int startIndex = 0;
        for (int i = 0; i < count; i++) {
            mChildRadians[startIndex + i] = Math.toRadians(degrees);
            degrees += sectorDegrees;
        }

        // The radians for the touch sector of an indicator.
        mTouchSectorRadians[0] = HIGHLIGHT_RADIANS;
        mEndVisibleRadians[0] = Math.toRadians(FIRST_LEVEL_END_DEGREES);
    }

    private void presetSecondLevelChildRadians() {
        int count = getChildCountByLevel(1);
        int sectors = (count <= 1) ? 1 : (count - 1);
        double sectorDegrees =
                ((SECOND_LEVEL_END_DEGREES - SECOND_LEVEL_START_DEGREES) / sectors);
        mSectorRadians[1] = Math.toRadians(sectorDegrees);

        double degrees = CLOSE_ICON_DEFAULT_DEGREES;
        mStartVisibleRadians[1] = Math.toRadians(SECOND_LEVEL_START_DEGREES);

        int startIndex = mSecondLevelStartIndex;
        for (int i = 0; i < count; i++) {
            mChildRadians[startIndex + i] = Math.toRadians(degrees);
            degrees += sectorDegrees;
        }

        // The radians for the touch sector of an indicator.
        mTouchSectorRadians[1] =
                Math.min(HIGHLIGHT_RADIANS, Math.toRadians(sectorDegrees));

        mEndVisibleRadians[1] = Math.toRadians(SECOND_LEVEL_END_DEGREES);
    }

    public void startTimeLapseAnimation(int timeLapseInterval, long startTime) {
        mTimeLapseInterval = timeLapseInterval;
        mRecordingStartTime = startTime;
        mNumberOfFrames = 0;
        invalidate();
    }

    public void stopTimeLapseAnimation() {
        mTimeLapseInterval = 0;
        invalidate();
    }

    private int getSelectedIndicatorIndex() {
        for (int i = 0; i < mIndicators.size(); i++) {
            AbstractIndicatorButton b = mIndicators.get(i);
            if (b.getPopupWindow() != null) {
                return indexOfChild(b);
            }
        }
        if (mPressedIndex != -1) {
            View v = getChildAt(mPressedIndex);
            if (!(v instanceof AbstractIndicatorButton) && v.isEnabled()) {
                return mPressedIndex;
            }
        }
        return -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw highlight.
        float delta = mStrokeWidth * 0.5f;
        float radius = (float) (mWheelRadius + mStrokeWidth * 0.5 + EDGE_STROKE_WIDTH);
        mBackgroundRect.set(mCenterX - radius, mCenterY - radius, mCenterX + radius,
                 mCenterY + radius);

        int selectedIndex = getSelectedIndicatorIndex();

        // Draw the highlight arc if an indicator is selected or being pressed.
       if (selectedIndex >= 0) {
            int degree = (int) Math.toDegrees(mChildRadians[selectedIndex]);
            mBackgroundPaint.setStrokeWidth(HIGHLIGHT_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            mBackgroundPaint.setColor(HIGHLIGHT_COLOR);
            canvas.drawArc(mBackgroundRect, -degree - HIGHLIGHT_DEGREES / 2,
                    HIGHLIGHT_DEGREES, false, mBackgroundPaint);
        }

        // Draw arc shaped indicator in time lapse recording.
        if (mTimeLapseInterval != 0) {
            // Setup rectangle and paint.
            mBackgroundRect.set((float)(mCenterX - mShutterButtonRadius),
                    (float)(mCenterY - mShutterButtonRadius),
                    (float)(mCenterX + mShutterButtonRadius),
                    (float)(mCenterY + mShutterButtonRadius));
            mBackgroundRect.inset(3f, 3f);
            mBackgroundPaint.setStrokeWidth(TIME_LAPSE_ARC_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            mBackgroundPaint.setColor(TIME_LAPSE_ARC_COLOR);

            // Compute the start angle and sweep angle.
            long timeDelta = SystemClock.uptimeMillis() - mRecordingStartTime;
            long numberOfFrames = timeDelta / mTimeLapseInterval;
            float sweepAngle;
            if (numberOfFrames > mNumberOfFrames) {
                // The arc just acrosses 0 degree. Draw a full circle so it
                // looks better.
                sweepAngle = 360;
                mNumberOfFrames = numberOfFrames;
            } else {
                sweepAngle = timeDelta % mTimeLapseInterval * 360f / mTimeLapseInterval;
            }

            canvas.drawArc(mBackgroundRect, 0, sweepAngle, false, mBackgroundPaint);
            invalidate();
        }

        super.onDraw(canvas);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mCurrentMode == MODE_VIDEO) {
            mSecondLevelIcon.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
            mCloseIcon.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
            requestLayout();
        } else {
            // We also disable the zoom button during snapshot.
            enableZoom(enabled);
        }
        mSecondLevelIcon.setEnabled(enabled);
        mCloseIcon.setEnabled(enabled);
    }

    public void enableZoom(boolean enabled) {
        if (mZoomIcon != null)  mZoomIcon.setEnabled(enabled);
    }

    public void onTouchOutBound() {
        dismissSettingPopup();
        if (mPressedIndex != -1) {
            injectMotionEvent(mPressedIndex, mLastMotionEvent, MotionEvent.ACTION_CANCEL);
            mPressedIndex = -1;
            invalidate();
        }
    }
}
