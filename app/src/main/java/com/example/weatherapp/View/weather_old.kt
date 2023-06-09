package com.example.weatherapp.View

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import com.example.weatherapp.Api.ApiUtilities
import com.example.weatherapp.Models.WeatherModel
import com.example.weatherapp.R
import com.example.weatherapp.databinding.FragmentWeatherOldBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class weather_old : Fragment() {

    private var _binding: FragmentWeatherOldBinding? = null
    private val binding get() = _binding!!

    private lateinit var currentLocation : Location
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 101
    private val apiKey = "YOUR API KEY"
    private var isElderlyMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentWeatherOldBinding.inflate(inflater, container, false)
        val view = binding.root

        // Set the visibility of the root view to "invisible" to avoid showing the default view
        view.visibility = View.INVISIBLE


        binding.citySearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                getCityWeather(binding.citySearch.text.toString())
                binding.citySearch.clearFocus()

                // Hide the keyboard
                val context = requireContext()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.citySearch.windowToken, 0)
                true
                return@setOnEditorActionListener true
            }
            false
        }

        binding.currentLocation.setOnClickListener {
            getCurrentLocation()
        }
        binding.elderlyMode.setOnClickListener {
            isElderlyMode = !isElderlyMode
        }

        val savedCityName = getSavedCityName()
        if (savedCityName != null) {
            binding.citySearch.setText(savedCityName)
            getCityWeather(savedCityName)
        } else {
            getCurrentLocation()
        }

        // Set the visibility of the root view to "visible" now that the correct view has been inflated
        view.visibility = View.VISIBLE

        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val savedCityName = getSavedCityName()
        if (savedCityName != null) {
            binding.citySearch.setText(savedCityName)
            getCityWeather(savedCityName)
        }
        //navigation to switch screen look to elderly users screen
        binding.elderlyMode.setOnClickListener{ view ->
            view.findNavController().navigate(R.id.action_weather_old_to_weather_normal)
        }
    }

    private fun saveCityName(cityName: String) {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(getString(R.string.saved_city_name_key), cityName)
            apply()
        }
    }

    private fun getSavedCityName(): String? {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        return sharedPref.getString(getString(R.string.saved_city_name_key), null)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
    private fun getCityWeather(city : String){
        if (isOnline()) {
            ApiUtilities.getApiInterface()?.getCityWeatherData(city, apiKey)?.enqueue(
                object : Callback<WeatherModel> {
                    override fun onResponse(
                        call: Call<WeatherModel>,
                        response: Response<WeatherModel>
                    ) {
                        if (response.isSuccessful) {
                            response.body()?.let {
                                setData(it)
                                // Save the city name to shared preferences
                                saveCityName(city)
                            }
                        } else {
                            Toast.makeText(requireActivity(), "No City Found", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                    override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                        Toast.makeText(requireActivity(), "Problem appeared", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
        } else {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCureentLocationWeather(latitude : String, longitude : String) {

        ApiUtilities.getApiInterface()?.getCurrentWeatherData(latitude, longitude, apiKey)
            ?.enqueue(object : Callback<WeatherModel> {
                override fun onResponse(
                    call: Call<WeatherModel>,
                    response: Response<WeatherModel>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let {
                            setData(it)
                        }
                    }
                }
                override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                    Toast.makeText(requireActivity(), "Problem appeared", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun getCurrentLocation() {
        if (!checkPermissions()) {
            requestPermission()
            return
        }

        if (!context?.let { isLocationEnabled(it) }!!) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            return
        }

        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED && context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
            return
        }

        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    //binding.progressBar.visibility = View.VISIBLE
                    fetchCureentLocationWeather(
                        location.latitude.toString(),
                        location.longitude.toString()
                    )
                }
            }
    }


    private fun requestPermission (){
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_REQUEST_CODE
        )
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    private fun checkPermissions() : Boolean {
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } == PackageManager.PERMISSION_GRANTED && context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            } == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                getCurrentLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setData (body : WeatherModel){

        binding.apply {
            val currentDate = SimpleDateFormat("dd/MM/yyyy hh:mm").format(Date())
            dateTime.text = currentDate.toString()
            maxTemp.text = "Max " + k2c(body?.main?.temp_max!!).toInt() + "°"
            minTemp.text = "Min " + k2c(body?.main?.temp_min!!).toInt() + "°"
            temp.text = "" + k2c(body?.main?.temp!!).toInt() + "°"
            weatherTitle.text = body.weather[0].main
            val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
            sunriseValue.text = Instant.ofEpochSecond(body.sys.sunrise.toLong())
                .atZone(ZoneId.systemDefault()).format(timeFormat)
            sunsetValue.text = Instant.ofEpochSecond(body.sys.sunset.toLong())
                .atZone(ZoneId.systemDefault()).format(timeFormat)
            pressureValue.text = body.main.pressure.toString()
            humidityValue.text = body.main.humidity.toString() + "%"
            citySearch.setText(body.name)
            feelsLike.text = "" + k2c(body?.main?.feels_like!!).toInt() + "°"
            windValue.text = body.wind.speed.toString() + "m/s"
            countryValue.text = body.sys.country
        }
        updateUI(body.weather[0].id)
    }

    private fun updateUI(id: Int) {
        binding.apply {
            when (id) {
                in 200..232 -> {
                    weatherImg.setImageResource(R.drawable.ic_storm_weather)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.thunderstrom_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.thunderstrom_bg)
                }
                in 500..531 -> {
                    weatherImg.setImageResource(R.drawable.ic_rainy_weather)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.rain_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.rain_bg)
                }
                in 600..622 -> {
                    weatherImg.setImageResource(R.drawable.ic_snow_weather)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.snow_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.snow_bg)
                }
                in 701..781 -> {
                    weatherImg.setImageResource(R.drawable.ic_broken_clouds)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.atmosphere_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.atmosphere_bg)
                }
                800 -> {
                    weatherImg.setImageResource(R.drawable.ic_clear_day)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clear_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clear_bg)
                }
                in 801..804 -> {
                    weatherImg.setImageResource(R.drawable.ic_cloudy_weather)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clouds_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clouds_bg)
                }
                else -> {
                    weatherImg.setImageResource(R.drawable.ic_unknown)
                    mainLayout2.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.unknown_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.unknown_bg)
                }
            }
        }
    }
    private fun k2c(t: Double): Double {
        var intTemp = t
        intTemp = intTemp.minus(273)
        return intTemp.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
    }

}