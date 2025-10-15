package com.example.jumprecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.jumprecorder.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenJump.setOnClickListener {
            startActivity(Intent(this, JumpHeightActivity::class.java))
        }

        binding.btnOpenWorkout.setOnClickListener {
            startActivity(Intent(this, WorkoutCounterActivity::class.java))
        }
    }
}
