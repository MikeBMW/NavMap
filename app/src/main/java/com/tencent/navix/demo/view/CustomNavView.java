package com.tencent.navix.demo.view;

import android.content.Context;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tencent.navix.api.layer.NavigatorLayer;
import com.tencent.navix.api.layer.NavigatorLayerRoot;
import com.tencent.navix.api.layer.NavigatorLayerRootDrive;
import com.tencent.navix.api.model.NavDriveDataInfo;
import com.tencent.navix.api.model.NavDriveRouteData;
import com.tencent.navix.api.model.NavLaneInfo;
import com.tencent.navix.api.model.NavMode;
import com.tencent.navix.api.navigator.NavigatorDrive;
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver;
import com.tencent.navix.demo.R;
import com.tencent.navix.ui.internal.NavPreviewButtonView;

import java.util.Locale;

public class CustomNavView extends RelativeLayout implements NavigatorLayer<NavigatorDrive> {

    private NavigatorDrive navigatorDrive;

    private ImageView turnIcon;
    private TextView distanceInfo;
    private ImageView guideLane;

    private NavDriveDataInfo navDriveDataInfo;

    private NavPreviewButtonView previewButtonView;

    private NavigatorLayerRootDrive layerRootDrive;

    public CustomNavView(@NonNull Context context) {
        super(context);
        init();
    }

    public CustomNavView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomNavView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.layout_custom_view, this);
        turnIcon = findViewById(R.id.turn_icon);
        distanceInfo = findViewById(R.id.distance_info);
        guideLane = findViewById(R.id.guide_lane);
        previewButtonView = findViewById(R.id.preview_button);
        setVisibility(View.GONE);

        previewButtonView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layerRootDrive != null) {
                    layerRootDrive.setNavMode(layerRootDrive.getNavMode() == NavMode.MODE_OVERVIEW
                            ? NavMode.MODE_3D_TOWARDS_UP : NavMode.MODE_OVERVIEW);
                }
            }
        });
    }

    public void bindLayerRoot(NavigatorLayerRootDrive layerRootDrive) {
        this.layerRootDrive = layerRootDrive;
        this.layerRootDrive.addNavModeChangeCallback(navModeChangeCallback);
    }

    public void unbindLayerRoot() {
        if (layerRootDrive != null) {
            layerRootDrive.removeNavModeChangeCallback(navModeChangeCallback);
        }
        layerRootDrive = null;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onViewBound(NavigatorDrive navigatorDrive) {
        this.navigatorDrive = navigatorDrive;
        this.navigatorDrive.registerObserver(navigatorDriveObserver, Looper.getMainLooper());
    }

    @Override
    public void onViewUnBound() {
        this.navigatorDrive.unregisterObserver(navigatorDriveObserver);
        this.navigatorDrive = null;
    }

    NavigatorLayerRoot.NavModeChangeCallback navModeChangeCallback = new NavigatorLayerRoot.NavModeChangeCallback() {
        @Override
        public void onNavModeChange(NavMode navMode, boolean b) {
            previewButtonView.onNavModeChange(navMode, b);
        }
    };

    SimpleNavigatorDriveObserver navigatorDriveObserver = new SimpleNavigatorDriveObserver() {
        @Override
        public void onNavDataInfoUpdate(NavDriveDataInfo navDriveDataInfo) {
            super.onNavDataInfoUpdate(navDriveDataInfo);
            if (CustomNavView.this.navDriveDataInfo == null) {
                setVisibility(View.VISIBLE);
            }
            CustomNavView.this.navDriveDataInfo = navDriveDataInfo;
            NavDriveRouteData mainRouteData = navDriveDataInfo.getMainRouteData();
            if (mainRouteData != null) {
                turnIcon.setImageBitmap(mainRouteData.getNextIntersectionBitmap());

                String text;
                int distance = mainRouteData.getNextIntersectionRemainingDistance();
                if (distance > 1000) {
                    text = String.format(Locale.getDefault(), "%.1f公里", distance / 1000f);
                } else if(distance <= 0){
                    text = "现在";
                } else {
                    text = String.format(Locale.getDefault(), "%d米", distance);
                }
                distanceInfo.setText(text);
            }
        }

        @Override
        public void onWillShowLaneGuide(NavLaneInfo laneInfo) {
            super.onWillShowLaneGuide(laneInfo);
            guideLane.setImageBitmap(laneInfo.getGuideLaneBitmap());
        }

        @Override
        public void onHideLaneGuide() {
            super.onHideLaneGuide();
            guideLane.setImageBitmap(null);
        }
    };
}
