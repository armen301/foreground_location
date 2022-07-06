package com.location.foreground

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.location.foreground.databinding.ActivityMain2Binding
import com.location.foreground_location.api.LocationService
import com.location.foreground_location.api.LocationUpdateConfig
import com.location.foreground_location.api.NotificationConfig

class Main2Activity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMain2Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        service = LocationService.init(this, LocationUpdateConfig(5000), NotificationConfig(), {
            Toast.makeText(this,"lat:${it.latitude}, long:${it.longitude}", Toast.LENGTH_SHORT).show()
        }){
            Toast.makeText(this, "error:$it", Toast.LENGTH_SHORT).show()
        }
    }

    lateinit var service: LocationService

    override fun onStart() {
        super.onStart()
        service.stop()
    }

    override fun onStop() {
        super.onStop()
        service.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
