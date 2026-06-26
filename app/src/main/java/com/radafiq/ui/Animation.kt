package com.radafiq.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object MotionTokens {
    const val Fast = 150
    const val Medium = 300
    const val Slow = 500
    const val MoneyCountUp = 700
    const val PressBounce = 100
}

object Easing {
    val fastOutSlowIn = FastOutSlowInEasing
    val linearOutSlowIn = LinearOutSlowInEasing
    val fastOutLinearIn = FastOutLinearInEasing
    val linear = LinearEasing
}

object Duration {
    val instant: TweenSpec<Float> = tween(MotionTokens.Fast, easing = Easing.fastOutSlowIn)
    val normal: TweenSpec<Float> = tween(MotionTokens.Medium, easing = Easing.fastOutSlowIn)
    val slow: TweenSpec<Float> = tween(MotionTokens.Slow, easing = Easing.fastOutSlowIn)
    val money: TweenSpec<Float> = tween(MotionTokens.MoneyCountUp, easing = Easing.fastOutSlowIn)
    val fade: TweenSpec<Float> = tween(MotionTokens.Medium, easing = Easing.linear)
    val tabSlide: TweenSpec<Float> = tween(MotionTokens.Medium, easing = Easing.fastOutSlowIn)
    val listStagger: TweenSpec<Float> = tween(MotionTokens.Medium)
    val pressBounce: TweenSpec<Float> = tween(MotionTokens.PressBounce, easing = Easing.fastOutSlowIn)
}
