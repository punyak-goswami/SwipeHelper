package com.developer.swipehelper.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.developer.swipehelper.R
import com.developer.swipehelper.annotations.EdgeGravity
import com.developer.swipehelper.utils.SwipeHelper

class SwipeToDismissLayout(context: Context, attributeSet: AttributeSet) :
    ViewGroup(context, attributeSet) {

    private var mDragHelper: ViewDragHelper
    private var mSwipeViewId: Int
    private var mSwipeView: View? = null
    private var mainViewRectClose: Rect = Rect()

    /**
     * Listener used to dispatch dismiss event.
     */
    private var mOnDismissListener: OnDismissListener? = null

    /**
     * Interface definition for a callback to be invoked when view is dismissed by swipe.
     */
    interface OnDismissListener {
        /**
         * Called when swipe view has been dismissed.
         */
        fun onDismiss()
    }

    init {
        context.theme.obtainStyledAttributes(attributeSet, R.styleable.SwipeToDismissLayout, 0, 0)
            .apply {
                mSwipeViewId =
                    getResourceId(R.styleable.SwipeToDismissLayout_swipeView, -1)
            }
        val dragCallback = ViewDragCallback()
        mDragHelper = ViewDragHelper.create(this, 1f, dragCallback)
        val density = resources.displayMetrics.density
        val minVel = MIN_FLING_VELOCITY * density
        mDragHelper.minVelocity = minVel
    }

    /**
     * Set a listener to be notified of swipe view dismiss event.
     * @param listener Listener to notify when swipe view gets dismissed.
     */
    fun setOnDismissListener(listener: OnDismissListener) {
        mOnDismissListener = listener
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val parentLeft = paddingLeft
        val parentRight = r - l - paddingRight
        val parentTop = paddingTop
        val parentBottom = b - t - paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (child.visibility == View.GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams

            var childLeft: Int
            var childTop: Int

            var childGravity = lp.gravity
            if (childGravity == -1) {
                childGravity = DEFAULT_CHILD_GRAVITY
            }
            val absoluteGravity = Gravity.getAbsoluteGravity(childGravity, layoutDirection)

            when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.CENTER_HORIZONTAL -> {
                    childLeft =
                        parentLeft + (parentRight - parentLeft - childWidth) / 2 + lp.leftMargin - lp.rightMargin
                }

                Gravity.RIGHT -> {
                    childLeft = parentRight - childWidth - lp.rightMargin
                }

                Gravity.LEFT -> {
                    childLeft = parentLeft + lp.leftMargin
                }

                else -> {
                    childLeft = parentLeft + lp.leftMargin
                }
            }

            when (childGravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.CENTER_VERTICAL -> {
                    childTop =
                        parentTop + (parentBottom - parentTop - childHeight) / 2 + lp.topMargin - lp.bottomMargin
                }

                Gravity.BOTTOM -> {
                    childTop = parentBottom - childHeight - lp.bottomMargin
                }

                Gravity.TOP -> {
                    childTop = parentTop + lp.topMargin
                }

                else -> {
                    childTop = parentTop + lp.topMargin
                }
            }

            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)

        }
        mSwipeView = findViewById(mSwipeViewId)
        setMainViewRects()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var largestChildWidth = 0
        var largestChildHeight = 0
        var childState = 0
        for (i in 0 until childCount) { //Find width and height of largest child
            val child = getChildAt(i)
            if (child.visibility == View.GONE) {
                continue
            }
            val lp = child.layoutParams as LayoutParams
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            largestChildWidth =
                child.measuredWidth.coerceAtLeast(largestChildWidth + lp.leftMargin + lp.rightMargin)
            largestChildHeight =
                child.measuredHeight.coerceAtLeast(largestChildHeight + lp.topMargin + lp.bottomMargin)
            childState = combineMeasuredStates(childState, child.measuredState)
        }

        largestChildWidth += paddingLeft + paddingRight
        largestChildHeight += paddingTop + paddingBottom

        largestChildWidth = largestChildWidth.coerceAtLeast(suggestedMinimumWidth)
        largestChildHeight = largestChildHeight.coerceAtLeast(suggestedMinimumHeight)

        setMeasuredDimension(
            resolveSizeAndState(largestChildWidth, widthMeasureSpec, childState),
            resolveSizeAndState(
                largestChildHeight,
                heightMeasureSpec,
                childState shl MEASURED_HEIGHT_STATE_SHIFT
            )
        )

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams

            val childWidthMeasureSpec = if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                val width =
                    measuredWidth - paddingLeft - paddingRight - lp.leftMargin - lp.rightMargin
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            } else {
                getChildMeasureSpec(
                    widthMeasureSpec,
                    paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
                    lp.width
                )
            }

            val childHeightMeasureSpec = if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                val height =
                    measuredHeight - paddingTop - paddingBottom - lp.topMargin - lp.bottomMargin
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            } else {
                getChildMeasureSpec(
                    heightMeasureSpec,
                    paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                    lp.height
                )
            }
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
        }

    }

    private fun setMainViewRects() {
        mSwipeView?.let { mainView ->
            mainViewRectClose.apply {
                left = mainView.left
                top = mainView.top
                right = mainView.right
                bottom = mainView.bottom
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return ev?.let { mDragHelper.shouldInterceptTouchEvent(it) } ?: false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            mDragHelper.processTouchEvent(it)
        }
        return true
    }

    override fun computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun close() {
        mSwipeView?.let {
            mDragHelper.smoothSlideViewTo(
                it,
                mainViewRectClose.left,
                mainViewRectClose.top
            )
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun dismissFromRightSide() {
        mSwipeView?.let {
            mDragHelper.smoothSlideViewTo(
                it,
                mainViewRectClose.right,
                mainViewRectClose.top
            )
            mOnDismissListener?.onDismiss()
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun dismissFromLeftSide() {
        mSwipeView?.let {
            mDragHelper.smoothSlideViewTo(
                it,
                mainViewRectClose.left - it.width,
                mainViewRectClose.top
            )
            mOnDismissListener?.onDismiss()
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Dismiss the swipe view from given edge and notify listeners if set.
     * @param edge [SwipeHelper.RIGHT] to dismiss swipe view from right side or [SwipeHelper.LEFT] to dismiss swipe view from left side.
     *
     */
    fun dismiss(@EdgeGravity edge: Int) {
        mSwipeView?.let {
            when (edge) {
                SwipeHelper.LEFT -> {
                    dismissFromLeftSide()
                }

                SwipeHelper.RIGHT -> {
                    dismissFromRightSide()
                }
            }
        }
    }

    private inner class ViewDragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child == mSwipeView
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            if (releasedChild == mSwipeView) {
                if (releasedChild.left < mainViewRectClose.left) { //Swiping towards left side
                    if (xvel < 0 || releasedChild.right <= ((mainViewRectClose.right - mainViewRectClose.left) / 2)) {
                        dismissFromLeftSide()
                    } else {
                        close()
                    }
                } else { //Swiping towards right side
                    if (xvel > 0 || releasedChild.left >= ((mainViewRectClose.right - mainViewRectClose.left) / 2)) {
                        dismissFromRightSide()
                    } else {
                        close()
                    }
                }
            }

            ViewCompat.postInvalidateOnAnimation(this@SwipeToDismissLayout)
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return left
        }

        override fun getViewHorizontalDragRange(child: View): Int {
            return measuredWidth
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            if (dy != 0) {
                mSwipeView?.offsetTopAndBottom(-dy)
            }
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(layoutParams: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return LayoutParams(layoutParams)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    class LayoutParams : MarginLayoutParams {
        var gravity = -1

        constructor(c: Context, attrs: AttributeSet) : super(c, attrs) {
            val a = c.obtainStyledAttributes(attrs, R.styleable.SwipeToDismissLayout_Layout)
            gravity = a.getInt(R.styleable.SwipeToDismissLayout_Layout_android_layout_gravity, -1)
            a.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height)

        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 400
        private const val DEFAULT_CHILD_GRAVITY = Gravity.TOP or Gravity.START
    }
}