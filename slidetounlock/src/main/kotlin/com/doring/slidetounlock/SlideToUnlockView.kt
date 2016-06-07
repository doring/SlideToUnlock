/*
 * Copyright (C) 2016 Doring
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
package com.doring.slidetounlock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

class SlideToUnlockView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
: View(context, attrs, defStyleAttr) {

    // TODO all these properties are need to invalidate this view when setter is called
    var text: String
    var thumbDrawable: Drawable? = null
    var thumbMargin: Int = 0
    var progress = 0f

    var isThumbPressed = false
    var rewindDurationMillis: Long
    var onSlideStateChangedListener: OnSlideStateChangedListener? = null

    private val textPaint: Paint
    private val textBounds: Rect
    private val thumbRect: Rect
    private val thumbBaseRect: Rect
    private var thumbTouchX = 0f

    private val isUnlockAcceptable: Boolean
        get() = progress > 0.99f

    interface OnSlideStateChangedListener {
        fun onSlideStateChanged(unlocked: Boolean, thumbPressed: Boolean, progress: Float)
    }

    init {
        val ta = context.theme.obtainStyledAttributes(
                attrs, R.styleable.SlideToUnlockView, defStyleAttr, 0)

        val textColor: Int
        // TODO change to theme's default text size
        var textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)

        try {
            thumbDrawable = ta.getDrawable(R.styleable.SlideToUnlockView_thumb)
            thumbMargin = ta.getDimensionPixelSize(R.styleable.SlideToUnlockView_marginThumb, 0)
            text = ta.getString(R.styleable.SlideToUnlockView_text) ?: ""
            // TODO color state list
            textColor = ta.getColor(R.styleable.SlideToUnlockView_textColor, Color.WHITE)
            textSize = ta.getDimension(R.styleable.SlideToUnlockView_textSize, textSize)
            rewindDurationMillis = ta.getDimension(R.styleable.SlideToUnlockView_rewindDurationMillis, 500L.toFloat()) as Long
        } finally {
            ta.recycle()
        }

        if (thumbDrawable == null) {
            val sd = ShapeDrawable(OvalShape())
            sd.apply {
                paint.color = textColor
                paint.style = Paint.Style.FILL
                paint.isAntiAlias = true
            }
            thumbDrawable = sd
        }

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG).apply textPaint@{
            this@textPaint.typeface = Typeface.DEFAULT
            this@textPaint.color = textColor
            this@textPaint.style = Paint.Style.FILL
            this@textPaint.textSize = textSize
        }
        textBounds = Rect()
        thumbRect = Rect()
        thumbBaseRect = Rect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        thumbRect.set(thumbMargin, thumbMargin,
                h - thumbMargin,
                h - thumbMargin)
        thumbBaseRect.set(thumbRect)

        textPaint.getTextBounds(text, 0, text.length, textBounds)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        progress = (thumbRect.left - thumbMargin) / (width - thumbRect.width() - (thumbMargin * 2)).toFloat()
        // TODO alpha factor
        val textAlpha = 255 - (255f * 2f * progress).toInt()
        textPaint.alpha = if (textAlpha >= 0) textAlpha else 0

        canvas.drawText(text,
                (width / 2f) - textBounds.exactCenterX() + (thumbBaseRect.centerX() / 2),
                (height / 2f) - textBounds.exactCenterY(),
                textPaint)

        thumbDrawable?.bounds = thumbRect
        thumbDrawable?.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isThumbPressed = thumbRect.contains(x.toInt(), y.toInt())
                if (isThumbPressed) {
                    thumbTouchX = x - thumbRect.left
                }
            }
            MotionEvent.ACTION_UP -> {
                isThumbPressed = false
                if (!isUnlockAcceptable) {
                    startRewind()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val touchPosition = x - thumbTouchX
                val mostLeft = thumbMargin
                val mostRight = width - thumbBaseRect.width() - thumbMargin

                if (touchPosition < mostLeft) {
                    moveThumbTo(mostLeft)
                } else if (mostRight < touchPosition) {
                    moveThumbTo(mostRight)
                } else {
                    moveThumbTo(touchPosition.toInt())
                }
                invalidate()
            }
        }

        onSlideStateChangedListener?.onSlideStateChanged(isUnlockAcceptable, true, progress)

        return isThumbPressed
    }

    private fun startRewind() {
        ValueAnimator.ofInt(thumbRect.left, thumbBaseRect.left)
                .apply {
                    duration = (rewindDurationMillis * progress).toLong()
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        if (isThumbPressed) {
                            it.cancel()
                        } else {
                            val value = it.animatedValue as Int
                            moveThumbTo(value)
                            invalidate()
                        }
                    }
                }.start()
    }

    private fun moveThumbTo(position: Int) {
        thumbRect.offsetTo(position, thumbRect.top)
    }

}
