package com.developer.swipehelper.annotations

import androidx.annotation.IntDef
import com.developer.swipehelper.utils.SwipeHelper

@IntDef(value = [SwipeHelper.LEFT, SwipeHelper.RIGHT])
@Retention(AnnotationRetention.SOURCE)
annotation class EdgeGravity