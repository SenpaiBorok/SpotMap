package student.projects.spotmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import student.projects.spotmap.model.DirectionsResponse

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatLng: LatLng? = null
    private val TAG = "MapFragment"
    private val apiKey = "AIzaSyBV_1ggMnx_5NOMi34eMzPqspaz2oeURCU"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), apiKey)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                place.latLng?.let { latLng ->
                    showDirectionsDialog(place.name ?: "Destination", latLng)
                }
            }
            override fun onError(status: com.google.android.gms.common.api.Status) {
                Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getCurrentLocation()

        mMap.setOnPoiClickListener { poi ->
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(poi.latLng).title(poi.name))?.showInfoWindow()
            showDirectionsDialog(poi.name, poi.latLng)
        }

        mMap.setOnMapClickListener { latLng ->
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("Custom Point"))?.showInfoWindow()
            showDirectionsDialog("Custom Point", latLng)
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLatLng = LatLng(it.latitude, it.longitude)
                mMap.isMyLocationEnabled = true
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 14f))
            } ?: Log.e(TAG, "Location is null")
        }
    }

    private fun showDirectionsDialog(placeName: String, destinationLatLng: LatLng) {
        AlertDialog.Builder(requireContext())
            .setTitle(placeName)
            .setMessage("Get directions or start navigation?")
            .setPositiveButton("Directions") { _, _ ->
                currentLatLng?.let { origin ->
                    val originStr = "${origin.latitude},${origin.longitude}"
                    val destStr = "${destinationLatLng.latitude},${destinationLatLng.longitude}"
                    fetchDirections(originStr, destStr)
                } ?: Log.e(TAG, "Current location not available yet")
            }
            .setNegativeButton("Navigate") { _, _ ->
                startNavigation(destinationLatLng)
            }
            .show()
    }

    private fun startNavigation(destination: LatLng) {
        val uri = "google.navigation:q=${destination.latitude},${destination.longitude}&mode=d"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
    }

    private fun fetchDirections(origin: String, destination: String) {
        val service = RetrofitClient.instance.create(DirectionsApiService::class.java)
        service.getDirections(origin, destination, apiKey).enqueue(object : Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                if (response.isSuccessful && response.body()?.routes?.isNotEmpty() == true) {
                    val route = response.body()!!.routes[0]
                    val decodedPath = PolylineDecoder.decode(route.overview_polyline.points)
                    mMap.addPolyline(
                        PolylineOptions()
                            .addAll(decodedPath)
                            .width(10f)
                            .color(android.graphics.Color.BLUE)
                            .geodesic(true)
                    )
                } else {
                    Log.e(TAG, "Directions API failed: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                Log.e(TAG, "Directions API call failed", t)
            }
        })
    }
}
