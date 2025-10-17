package com.example.motionanalysis.domain.model

data class PoseKeypoint(
    val name: String,
    val x: Float,
    val y: Float,
    val confidence: Float
)
