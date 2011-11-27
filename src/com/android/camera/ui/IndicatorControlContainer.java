/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import java.util.HashMap;

public abstract class IndicatorControlContainer extends IndicatorControl
        implements OnIndicatorEventListener {
    public abstract void initialize(Context context, PreferenceGroup group,
            boolean isZoomSupported, String[] keys, /*String[]*/HashMap otherSettingKeys);

    public IndicatorControlContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public abstract void enableZoom(boolean enabled);

    public abstract void dismissSecondLevelIndicator();
}
