package com.ansimdrive.pleos

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.ansimdrive.pleos.databinding.ActivityMainBinding
import com.ansimdrive.pleos.ui.ParkedFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(TAG, "MainActivity created (demoMode=${BuildConfig.DEMO_MODE})")

        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(R.id.container, ParkedFragment()) }
        }
    }
}
