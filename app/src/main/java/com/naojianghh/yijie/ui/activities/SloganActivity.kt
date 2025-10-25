package com.naojianghh.yijie.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import com.naojianghh.yijie.R
import com.naojianghh.yijie.databinding.ActivitySloganBinding
import kotlin.apply
import kotlin.jvm.java
import kotlin.let

class SloganActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySloganBinding

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_slogan)
        binding.lifecycleOwner = this

        enableFullscreen()



        binding.button.setOnClickListener {
            val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
            sharedPreferences.edit().apply {
                putBoolean("hasShowSplash", true)
                apply()
            }

            val intent = Intent(this@SloganActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val hasShowSplash = sharedPreferences.getBoolean("hasShowSplash", false)
//        if (hasShowSplash) {
//            val intent = Intent(this, MainActivity::class.java)
//            startActivity(intent)
//            finish()
//        }


    }

    private fun enableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
        window.isNavigationBarContrastEnforced = false
            }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->

                controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }


        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }



}