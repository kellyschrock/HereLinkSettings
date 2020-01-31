package com.fognl.herelink.herelinksettings

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_d2d_old.setOnClickListener { onOldD2d() }
        btn_d2d_new.setOnClickListener { onNewD2d() }
    }

    private fun onOldD2d() {
        startActivity(Intent(this, D2DInfoActivity::class.java))
    }

    private fun onNewD2d() {
        startActivity(Intent(this, D2DSettingsActivity::class.java))
    }
}
