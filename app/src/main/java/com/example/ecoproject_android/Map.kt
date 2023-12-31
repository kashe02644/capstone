package com.example.ecoproject_android

import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import com.google.android.material.card.MaterialCardView
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.widget.LocationButtonView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*




class Map : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mapView: MapView
    private val LOCATION_PERMISSTION_REQUEST_CODE: Int = 1000
    private lateinit var locationSource: FusedLocationSource
    private lateinit var naverMap: NaverMap
    private var currentLocation: LatLng? = null
    val pathOverlay = PathOverlay()

    private lateinit var addresscardview: MaterialCardView
    private lateinit var addresstext : TextView
    private lateinit var citytext : TextView
    private lateinit var pathimg : ImageButton

    private var citydata = ArrayList<String>()
    private var addressdata = ArrayList<String>()
    var testlat : Double =0.0
    var testlng : Double =0.0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        //뒤로가기
        val back = findViewById<Button>(R.id.back)
        addresscardview = findViewById(R.id.addresscardview)
        addresstext = findViewById(R.id.addresstext)
        citytext = findViewById(R.id.citytext)
        pathimg = findViewById(R.id.pathimg)

        back.setOnClickListener { finish() }

        locationSource = FusedLocationSource(this, LOCATION_PERMISSTION_REQUEST_CODE)
        //getRouteData()
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this);
    }
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions,
                grantResults)) {
            if (!locationSource.isActivated) { // 권한 거부됨
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }




    override fun onMapReady(naverMap: NaverMap) {
        //네이버맵 가져옴
        this.naverMap = naverMap

        //현재위치
        naverMap.locationSource = locationSource

        //네이버맵 ui 현재위치 버튼등
        val uiSettings = naverMap.uiSettings
        naverMap.locationTrackingMode = LocationTrackingMode.Follow
        uiSettings.isLocationButtonEnabled = false
        val LocationButtonView = findViewById(R.id.location) as LocationButtonView
        LocationButtonView.map = naverMap

        // 카메라 초기 위치 설정
        val initialPosition = LatLng(37.506855, 127.066242)
        val cameraUpdate = CameraUpdate.scrollTo(initialPosition)
        naverMap.moveCamera(
            CameraUpdate.toCameraPosition(
                CameraPosition(
                    NaverMap.DEFAULT_CAMERA_POSITION.target,
                    NaverMap.DEFAULT_CAMERA_POSITION.zoom
                )
            )
        )



        /*여기서 부터 의류 수거함 마커*/
        // 마커들 위치 정의
        getVal()//db가져오는 메소드드


        // 카메라 이동 되면 호출 되는 이벤트
        naverMap.addOnCameraChangeListener { reason, animated -> freeActiveMarkers()
            // 정의된 마커위치들중 가시거리 내에있는것들만 마커 생성
            val currentPosition = getCurrentPosition(naverMap)
            for (markerPosition in markersPosition!!) {
                if (!withinSightMarker(currentPosition, markerPosition)) continue
                val marker = Marker()
                marker.isHideCollidedCaptions = true //마커 겹치게하는 메소드
                marker.isHideCollidedMarkers = true  //마커 겹치게하는 메소드
                marker.position = markerPosition
                marker.setOnClickListener(Overlay.OnClickListener {

                    addresscardview.visibility = VISIBLE
                    addresstext.text = addressdata[markersPosition!!.indexOf(markerPosition)]
                    citytext.text = citydata[markersPosition!!.indexOf(markerPosition)]
                    testlat = marker.position.latitude
                    testlng = marker.position.longitude

                    false
                })

                marker.map = naverMap
                activeMarkers?.add(marker)
            }
        }

        naverMap.addOnLocationChangeListener { location ->
            currentLocation = LatLng(location.latitude, location.longitude)
        }

        pathimg.setOnClickListener {
            currentLocation?.let { current ->

                val destination = LatLng(testlat, testlng)
                getRouteData(current, destination)
            } ?: Toast.makeText(this, "현재 위치를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 마커 정보 저장시킬 변수들 선언
    private var markersPosition: Vector<LatLng>? = null
    var coordinates = mutableListOf<LatLng>()
    private var activeMarkers: Vector<Marker>? = null

    // 현재 카메라가 보고있는 위치
    fun getCurrentPosition(naverMap: NaverMap): LatLng {
        val cameraPosition = naverMap.cameraPosition
        return LatLng(cameraPosition.target.latitude, cameraPosition.target.longitude)
    }

    // 선택한 마커의 위치가 가시거리(카메라가 보고있는 위치 반경 3km 내)에 있는지 확인
    val REFERANCE_LAT = 1 / 109.958489129649955
    val REFERANCE_LNG = 1 / 88.74
    val REFERANCE_LAT_X3 = 3 / 109.958489129649955
    val REFERANCE_LNG_X3 = 3 / 88.74
    fun withinSightMarker(currentPosition: LatLng, markerPosition: LatLng): Boolean {
        val withinSightMarkerLat =
            Math.abs(currentPosition.latitude - markerPosition.latitude) <= REFERANCE_LAT_X3
        val withinSightMarkerLng =
            Math.abs(currentPosition.longitude - markerPosition.longitude) <= REFERANCE_LNG_X3
        return withinSightMarkerLat && withinSightMarkerLng
    }

    // 지도상에 표시되고있는 마커들 지도에서 삭제
    private fun freeActiveMarkers() {
        if (activeMarkers == null) {
            activeMarkers = Vector<Marker>()
            return
        }
        for (activeMarker in activeMarkers!!) {
            activeMarker.map = null
        }
        activeMarkers = Vector<Marker>()
    }

    /*여기까지 보이는 화면 부분만 마커 표시*/

    //위경도 db블러옴
    open fun getVal() {
        val dbHelper = DataBaseHelper(this)
        val db = dbHelper.readableDatabase
        val cursor: Cursor =
            db.rawQuery("SELECT * FROM LatLng", null)
        //" and name = ?",new String[]{"홍길동"});
        markersPosition = Vector()
        addressdata = ArrayList()
        citydata = ArrayList()
        while (cursor.moveToNext()) {
            val latitude = cursor.getDouble(0)
            val longitude = cursor.getDouble(1)
            val city = cursor.getString(2)
            val address = cursor.getString(3)


            markersPosition!!.add(LatLng(latitude, longitude))
            citydata.add(city)
            addressdata.add(address)
        }

        cursor.close()
        dbHelper.close()
    }

    private fun getRouteData(start: LatLng, goal: LatLng) {
        val clientId = "gltf29rywr" // 클라이언트 ID
        val clientSecret = "pp0hbjM5trHLFo0r1lazxHTDI7se0njR8o1opx9V" // 클라이언트 시크릿
        val apiUrl = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?"

        val apiUrlWithParams = apiUrl + "start=${start.longitude},${start.latitude}&goal=${goal.longitude},${goal.latitude}"

        // 네트워크 호출을 앱의 생명 주기에 맞추려면 GlobalScope 대신 lifecycleScope를 사용하십시오.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(apiUrlWithParams)
                val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                connection.setRequestMethod("GET")
                connection.setRequestProperty("X-NCP-APIGW-API-KEY-ID", clientId)
                connection.setRequestProperty("X-NCP-APIGW-API-KEY", clientSecret)
                val responseCode: Int = connection.getResponseCode()

                if (responseCode == 200) {
                    // 성공적인 응답
                    val input = BufferedReader(InputStreamReader(connection.getInputStream()))
                    val responseBody = StringBuilder()
                    var line: String?
                    while (input.readLine().also { line = it } != null) {
                        responseBody.append(line)
                        Log.d("파싱", line.toString())

                        //JsonParser.jsonParser(line)

                        try {
                            val jsonObject = JSONObject(line)
                            val routeArray = jsonObject.getJSONObject("route").getJSONArray("traoptimal")

                            for (i in 0 until routeArray.length()) {
                                val routeObject = routeArray.getJSONObject(i)
                                val pathArray = routeObject.getJSONArray("path")

                                for (j in 0 until pathArray.length()) {

                                    val path = pathArray.getJSONArray(j)
                                    val longitude = path.getDouble(0)
                                    val latitude = path.getDouble(1)
                                    coordinates.add(LatLng(latitude, longitude))
                                    // 경로 좌표 사용
                                    Log.d("path", "Longitude: $longitude, Latitude: $latitude")
                                }
                            }
                        } catch (e: JSONException) {
                            Log.d("error", e.toString())
                            // 예외 처리
                        }

                    }
                    input.close()

                    withContext(Dispatchers.Main) {
                        Log.d("API Result", responseBody.toString())
                        drawPath(coordinates)

                    }
                } else {
                    // 오류 응답
                    val error = BufferedReader(InputStreamReader(connection.getErrorStream()))
                    val errorMessage = StringBuilder()
                    var line: String?
                    while (error.readLine().also { line = it } != null) {
                        errorMessage.append(line)
                    }
                    error.close()

                    withContext(Dispatchers.Main) {
                        Log.e("API Call Failure", errorMessage.toString())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API Call Exception", "예외 발생")
                    Log.e("API Call Exception", e.toString())
                    Log.e("API Call Exception", "예외 발생")

                }
                e.printStackTrace()
            }
        }
    }


    // 경로를 그리는 메소드
    private fun drawPath(coordinates: List<LatLng>) {
        this.coordinates = mutableListOf()

        if (pathOverlay != null) {
            pathOverlay!!.map = null
        }
        pathOverlay.coords = coordinates
        pathOverlay.map = naverMap
    }



}