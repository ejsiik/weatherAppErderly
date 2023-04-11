package com.example.weatherapp.Api

import android.content.ContentValues.TAG
import android.util.Log
import com.example.weatherapp.Models.WeatherModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {

    @GET("weather")
    fun getCurrentWeatherData(
        @Query("lat") lat : String,
        @Query("lon") long : String,
        @Query("APPID") appid : String

    ):Call<WeatherModel>

    @GET("weather")
    fun getCityWeatherData(
        @Query("q") q : String,
        @Query("APPID") appid : String,
    ):Call<WeatherModel>

}

class MyCallback : Callback<WeatherModel> {
    override fun onResponse(call: Call<WeatherModel>, response: Response<WeatherModel>) {
        // Handle the response here
        if (response.isSuccessful) {
            val weather = response.body()
            // ...
        } else {
            Log.e(TAG, "Error fetching current location weather: ${response.code()}")
        }
    }

    override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
        // Handle the error here
        Log.e(TAG, "Error fetching current location weather", t)
    }
}