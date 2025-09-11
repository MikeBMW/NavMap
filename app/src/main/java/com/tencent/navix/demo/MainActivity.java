package com.tencent.navix.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tencent.example.location.DemoMapActivity;
import com.tencent.navix.api.NavigatorZygote;
import com.tencent.navix.demo.navi.CustomLocatorNavActivity;
import com.tencent.navix.demo.navi.CustomMarkerNavActivity;
import com.tencent.navix.demo.navi.CustomRouteStyleNavActivity;
import com.tencent.navix.demo.navi.CustomViewNavActivity;
import com.tencent.navix.demo.navi.DialogBasedNavActivity;
import com.tencent.navix.demo.navi.GpsNavActivity;
import com.tencent.navix.demo.navi.GpsNavLocActivity;
import com.tencent.navix.demo.navi.LandscapeSimulationNavActivity;
import com.tencent.navix.demo.navi.PlaybackNavActivity;
import com.tencent.navix.demo.navi.RideGpsNavActivity;
import com.tencent.navix.demo.navi.RideSimNavActivity;
import com.tencent.navix.demo.navi.SimulationNavActivity;
import com.tencent.navix.demo.navi.SimulationSliceNavActivity;
import com.tencent.navix.demo.navi.WalkGpsNavActivity;
import com.tencent.navix.demo.navi.WalkSimNavActivity;
import com.tencent.navix.demo.permission.BasePermissionActivity;
import com.tencent.navix.demo.route.CustomRouteRequestActivity;
import com.tencent.navix.demo.route.MapActivity;
import com.tencent.navix.demo.route.RouteRequestActivity;
import com.tencent.navix.demo.route.TruckRoutePlanActivity;
import com.tencent.navix.demo.route.TruckRouteRequestActivity;
import com.tencent.navix.demo.view.CustomEnlargedMapActivity;

import java.util.Objects;

public class MainActivity extends BasePermissionActivity {

    Object[][] functions = {
            {"项目定制：定位+导航功能", GpsNavLocActivity.class}, // 新增这一行，就能在主界面看到并点击启动了
            {"Map", MapActivity.class},
//            {"定位", MapLocationActivity.class},
            {"定位功能", DemoMapActivity.class}, // 新增：添加定位页面入口
            {"驾车路径规划", RouteRequestActivity.class},
            {"驾车真实导航", GpsNavActivity.class},
            {"驾车模拟导航", SimulationNavActivity.class},
            {"驾车模拟导航(横屏)", LandscapeSimulationNavActivity.class},
            {"驾车回放导航", PlaybackNavActivity.class},
            {"步行真实导航", WalkGpsNavActivity.class},
            {"骑行真实导航", RideGpsNavActivity.class},
            {"自定义导航面板", CustomViewNavActivity.class},
            {"自定义起/终/途径点Marker", CustomMarkerNavActivity.class},
            {"自定义自车标", CustomLocatorNavActivity.class},
            {"自定义路线样式", CustomRouteStyleNavActivity.class},
            {"自定义放大图位置", CustomEnlargedMapActivity.class},
            {"自定义避让区域路径规划", CustomRouteRequestActivity.class},
            {"货车路径规划", TruckRouteRequestActivity.class},
            {"货车路径规划2", TruckRoutePlanActivity.class},
            {"Dialog导航", DialogBasedNavActivity.class},
            {"分片算路模拟导航", SimulationSliceNavActivity.class},
            {"步行模拟导航", WalkSimNavActivity.class},
            {"骑行模拟导航", RideSimNavActivity.class},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((TextView)findViewById(R.id.info_sdk_version)).setText("导航SDK版本: " + NavigatorZygote.with(this).context().getVersion());
        ((TextView)findViewById(R.id.info_device_id)).setText("设备ID: " + NavigatorZygote.with(this).context().getConfig().getDeviceId());
        ((TextView)findViewById(R.id.info_privacy)).setText("隐私协议: https://privacy.qq.com/document/preview/ba5687d1f4054fed8e108645255320f7");
        ((RecyclerView)findViewById(R.id.recycler_view)).setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        ((RecyclerView)findViewById(R.id.recycler_view)).setAdapter(adapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        dividerItemDecoration.setDrawable(Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.list_divider, getTheme())));
        ((RecyclerView)findViewById(R.id.recycler_view)).addItemDecoration(dividerItemDecoration);
    }

    private final RecyclerView.Adapter<SimpleViewHolder> adapter = new RecyclerView.Adapter<SimpleViewHolder>() {

        @Override
        public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SimpleViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.item_view, null));
        }

        @Override
        public void onBindViewHolder(SimpleViewHolder holder, int position) {
            holder.name.setText((String)functions[position][0]);
            holder.itemView.setOnClickListener(view -> MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) functions[holder.getBindingAdapterPosition()][1])));
        }

        @Override
        public int getItemCount() {
            return functions.length;
        }
    };

    static class SimpleViewHolder extends RecyclerView.ViewHolder {

        final TextView name;

        public SimpleViewHolder(View itemView) {
            super(itemView);
            this.name = itemView.findViewById(R.id.item_view_name);
        }
    }
}
