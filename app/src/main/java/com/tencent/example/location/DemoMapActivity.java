package com.tencent.example.location;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
// 定位app新增
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.appcompat.app.AppCompatActivity;
//import java.util.Pair;
import android.util.Pair;


import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.navix.demo.R;
import com.tencent.tencentmap.mapsdk.maps.CameraUpdate;
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory;
import com.tencent.tencentmap.mapsdk.maps.MapView;
import com.tencent.tencentmap.mapsdk.maps.TencentMap;
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer;
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory;
import com.tencent.tencentmap.mapsdk.maps.model.Circle;
import com.tencent.tencentmap.mapsdk.maps.model.CircleOptions;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;
import com.tencent.tencentmap.mapsdk.maps.model.Marker;
import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions;

/**
 * 在腾讯地图上显示我的位置.
 *
 * <p>
 * 地图SDK相关内容请参考<a
 * href="http://open.map.qq.com/android_v1/index.html">腾讯地图SDK</a>
 */
public class DemoMapActivity extends Activity implements
		TencentLocationListener {

	private TextView mStatus;
	private MapView mMapView;
	private TencentMap mTencentMap;
    private Marker mLocationMarker;
    private Circle mAccuracyCircle;

	private TencentLocation mLocation;
	private TencentLocationManager mLocationManager;

	// 用于记录定位参数, 以显示到 UI
	private String mRequestParams;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_demo_map);
//       TencentMapInitializer.setAgreePrivacy(true);
		// 补充 Context 参数（this 代表当前 Activity 的 Context）
		TencentMapInitializer.setAgreePrivacy(this, true);
		mStatus = (TextView) findViewById(R.id.status);
		mStatus.setTextColor(Color.RED);
		initMapView();

		// 步骤 1：获取 AndroidManifest.xml 中配置的 TencentMapSDK Key
		String sdkKey = "";
		try {
			ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
					getPackageName(), PackageManager.GET_META_DATA);
			if (appInfo.metaData != null) {
				sdkKey = appInfo.metaData.getString("TencentMapSDK");
			}
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		// 步骤 2：构造 Pair 对象（第二个参数若不需要 secret 可传空字符串）
		Pair<String, String> keyPair = new Pair<>(sdkKey, "");

		// 步骤 3：初始化 TencentLocationManager
		mLocationManager = TencentLocationManager.getInstance(this, keyPair);

//		mLocationManager = TencentLocationManager.getInstance(this);
		// 设置坐标系为 gcj-02, 缺省坐标为 gcj-02, 所以通常不必进行如下调用
		mLocationManager.setCoordinateType(TencentLocationManager.COORDINATE_TYPE_GCJ02);
	}

	private void initMapView() {
		mMapView = (MapView) findViewById(R.id.mapviewOverlay);
		mTencentMap = mMapView.getMap();
		CameraUpdate cameraUpdate = CameraUpdateFactory.zoomTo(9);
		mTencentMap.moveCamera(cameraUpdate);
	}

	@Override
	protected void onResume() {
		mMapView.onResume();
		super.onResume();
		startLocation();
	}

	@Override
	protected void onPause() {
		mMapView.onPause();
		super.onPause();
		stopLocation();
	}

	@Override
	protected void onDestroy() {
		mMapView.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onStart() {
		mMapView.onStart();
		super.onStart();
	}

	@Override
	protected void onStop() {
		mMapView.onStop();
		super.onStop();
	}

	// ===== view listeners
//	public void myLocation(View view) {
//		if (mLocation != null) {
//			mMapView.getController().animateTo(Utils.of(mLocation));
//		}
//	}

	// ===== view listeners

	// ====== location callback
	@Override
	public void onNmeaMsgChanged(String nmea) {
		// 空实现（NMEA是卫星定位的原始数据协议，暂时用不到可忽略）
	}

	@Override
	public void onGnssInfoChanged(Object gnssInfo) {
		// 空实现（如果不需要处理GNSS信息）
		// 若需要处理，可根据官方文档解析gnssInfo对象（可能是GNSS状态、卫星数量等信息）
	}

	@Override
	public void onLocationChanged(TencentLocation location, int error,
			String reason) {
		if (error == TencentLocation.ERROR_OK) {
			mLocation = location;

			// 定位成功
			StringBuilder sb = new StringBuilder();
			sb.append("定位参数=").append(mRequestParams).append("\n");
			sb.append("(纬度=").append(location.getLatitude()).append(",经度=")
					.append(location.getLongitude()).append(",精度=")
					.append(location.getAccuracy()).append("), 来源=")
					.append(location.getProvider()).append(", 地址=")
					.append(location.getAddress());
            LatLng latLngLocation = new LatLng(location.getLatitude(), location.getLongitude());

			// 更新 status
			mStatus.setText(sb.toString());

			// 更新 location Marker
            if (mLocationMarker == null) {
                mLocationMarker =
                        mTencentMap.addMarker(new MarkerOptions().
                                position(latLngLocation).
                                icon(BitmapDescriptorFactory.fromResource(R.drawable.mark_location)));
            } else {
                mLocationMarker.setPosition(latLngLocation);
				CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(latLngLocation);
				mTencentMap.moveCamera(cameraUpdate);
            }

            if (mAccuracyCircle == null) {
                mAccuracyCircle = mTencentMap.addCircle(new CircleOptions().
                        center(latLngLocation).
                        radius(location.getAccuracy()).
                        fillColor(0x884433ff).
                        strokeColor(0xaa1122ee).
                        strokeWidth(1));
            } else {
                mAccuracyCircle.setCenter(latLngLocation);
                mAccuracyCircle.setRadius(location.getAccuracy());
            }
		}
	}

	@Override
	public void onStatusUpdate(String name, int status, String desc) {
		// ignore
	}

	// ====== location callback

	private void startLocation() {
		TencentLocationRequest request = TencentLocationRequest.create();
		request.setInterval(5000);
//		mLocationManager.requestLocationUpdates(request, this);
		// 调用三参数版本：第三个参数传 0（内部自动处理 Looper）
		mLocationManager.requestLocationUpdates(
				request,
				this,
				0 // 新增的第三个参数（int 类型，可传 0）
		);

		mRequestParams = request.toString() + ", 坐标系="
				+ DemoUtils.toString(mLocationManager.getCoordinateType());
	}

	private void stopLocation() {
		mLocationManager.removeUpdates(this);
	}

}
