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
import com.example.weatherapp.databinding.FragmentWeatherNormalBinding
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


class weather_normal : Fragment() {

    private var _binding: FragmentWeatherNormalBinding? = null
    private val binding get() = _binding!!

    private lateinit var currentLocation : Location
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 101
    private val apiKey = "84e0d7446cda7ae84fde1cf9ae957e49"
    private var isElderlyMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment using data binding
        _binding = FragmentWeatherNormalBinding.inflate(inflater, container, false)
        val view = binding.root

        // Set listener for the city search edit text
        binding.citySearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Call the function to get weather data for the searched city
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

        // Set listeners for the location and elderly mode buttons
        binding.currentLocation.setOnClickListener {
            getCurrentLocation()
        }
        binding.elderlyMode.setOnClickListener {
            isElderlyMode = !isElderlyMode
        }

        // Check if there is a saved city name, and if so, display the weather data for that city
        // Otherwise, get the weather data for the current location
        val savedCityName = getSavedCityName()
        if (savedCityName != null) {
            binding.citySearch.setText(savedCityName)
            getCityWeather(savedCityName)
        } else {
            getCurrentLocation()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if there is a saved city name, and if so, display the weather data for that city
        val savedCityName = getSavedCityName()
        if (savedCityName != null) {
            binding.citySearch.setText(savedCityName)
            getCityWeather(savedCityName)
        }

        // Set listener for the elderly mode button to navigate to the elderly mode screen
        binding.elderlyMode.setOnClickListener{ view ->
            view.findNavController().navigate(R.id.action_weather_normal_to_weather_old)
        }
    }

    // Function to save the city name to shared preferences
    private fun saveCityName(cityName: String) {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(getString(R.string.saved_city_name_key), cityName)
            apply()
        }
    }

    // Function to get the saved city name from shared preferences
    private fun getSavedCityName(): String? {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        return sharedPref.getString(getString(R.string.saved_city_name_key), null)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    // Function to get weather data for a specific city using the OpenWeatherMap API
    private fun getCityWeather(city : String) {
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

    // Function to get weather data for the current location using the OpenWeatherMap API
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

    private fun isLocationEnabled(): Boolean {
        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Function to get the current location and fetch weather data for it using the OpenWeatherMap API
    private fun getCurrentLocation() {
        // Check if location permissions have been granted
        if (!checkPermissions()) {
            requestPermission()
            return
        }

        // Check if location is enabled on the device
        if (!context?.let { isLocationEnabled(it) }!!) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            return
        }

        if (!isLocationEnabled()) {
            Toast.makeText(requireContext(), "Please enable GPS", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if location permissions have been granted again (just in case)
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

        // Get the last known location of the device and fetch weather data for it
        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    fetchCureentLocationWeather(
                        location.latitude.toString(),
                        location.longitude.toString()
                    )
                }
            }
    }

    // Function to request location permissions
    private fun requestPermission (){
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_REQUEST_CODE
        )
    }

    // Function to check if location is enabled on the device
    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    private fun checkPermissions() : Boolean {
        // Check if the required permissions have been granted
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
        // Handle the result of the permission request
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                getCurrentLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setData (body : WeatherModel){
        // Set the weather data in the views
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
        // Update the UI based on the weather condition
        binding.apply {
            when (id) {
                in 200..232 -> {
                    weatherImg.setImageResource(R.drawable.ic_storm_weather)
                    mainLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.thunderstrom_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.thunderstrom_bg)
                }
                in 500..531 -> {
                    weatherImg.setImageResource(R.drawable.ic_rainy_weather)
                    mainLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.rain_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.rain_bg)
                }
                in 600..622 -> {
                    weatherImg.setImageResource(R.drawable.ic_snow_weather)
                    mainLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.snow_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.snow_bg)
                }
                in 701..781 -> {
                    weatherImg.setImageResource(R.drawable.ic_broken_clouds)
                    mainLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.atmosphere_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.atmosphere_bg)
                }
                800 -> {
                    weatherImg.setImageResource(R.drawable.ic_clear_day)
                    mainLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clear_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clear_bg)
                }
                in 801..804 -> {
                    weatherImg.setImageResource(R.drawable.ic_cloudy_weather)
                    mainLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clouds_bg)
                    optionsLayout.background = ContextCompat
                        .getDrawable(requireContext(), R.drawable.clouds_bg)
                }
                else -> {
                    weatherImg.setImageResource(R.drawable.ic_unknown)
                    mainLayout.background = ContextCompat
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