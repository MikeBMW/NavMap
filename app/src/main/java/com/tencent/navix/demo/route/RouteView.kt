package com.tencent.navix.demo.route

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.tencent.navix.api.map.MapApi
import com.tencent.navix.api.model.NavRoutePlan
import com.tencent.navix.core.NavigatorContext
import com.tencent.navix.demo.R
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.model.*

class RouteView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val itemView1: RouteItemView
    private val itemView2: RouteItemView
    private val itemView3: RouteItemView

    private var selectIndex = 0

    private lateinit var tencentMap: MapApi
    private var plan: NavRoutePlan<*>? = null

    private val DP_20 =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            20f,
            NavigatorContext.share().applicationContext.resources.displayMetrics
        ).toInt()

    private val ROUTE_COLOR_MAIN: Int = 0xFF00CC66.toInt()
    private val ROUTE_COLOR_MAIN_STROKE: Int = 0xFF009449.toInt()
    private val ROUTE_COLOR_BACKUP: Int = 0xFFAFDBC7.toInt()
    private val ROUTE_COLOR_BACKUP_STROKE: Int = 0xFF8BB8A3.toInt()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_route, this)

        findViewById<LinearLayout>(R.id.route_item_container).apply {
            itemView1 = RouteItemView(context, attrs)
            itemView2 = RouteItemView(context, attrs)
            itemView3 = RouteItemView(context, attrs)
            addView(itemView1, 0, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(itemView2,0, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(itemView3,0, ViewGroup.LayoutParams.MATCH_PARENT)
            (itemView1.layoutParams as LayoutParams).weight = 1f
            (itemView2.layoutParams as LayoutParams).weight = 1f
            (itemView3.layoutParams as LayoutParams).weight = 1f

        }
    }

    fun injectMap(map: MapApi) {
        tencentMap = map
    }

    fun currentIndex(): Int {
        return selectIndex
    }

    fun updateRoutePlan(routePlan: NavRoutePlan<*>?) {
        routePlan?.apply {
            routePlan.routeDatas?.apply {
                if (routeDatas.size > 2) {
                    itemView3.visibility = View.VISIBLE
                    itemView3.update(routeDatas[2], "${routeDatas[2].tag}")

                    itemView3.setOnClickListener {
                        itemView3.setSelect(true)
                        itemView2.setSelect(false)
                        itemView1.setSelect(false)
                        selectIndex = 2
                        drawRoute()
                    }
                } else {
                    itemView3.visibility = View.INVISIBLE
                    itemView3.setOnClickListener(null)
                }

                if (routeDatas.size > 1) {
                    itemView2.visibility = View.VISIBLE
                    itemView2.update(routeDatas[1], "${routeDatas[1].tag}")

                    itemView2.setOnClickListener {
                        itemView3.setSelect(false)
                        itemView2.setSelect(true)
                        itemView1.setSelect(false)
                        selectIndex = 1
                        drawRoute()
                    }
                } else {
                    itemView2.visibility = View.INVISIBLE
                    itemView2.setOnClickListener(null)

                }

                if (routeDatas.size > 0) {
                    itemView1.visibility = View.VISIBLE
                    itemView1.update(routeDatas[0], "${routeDatas[0].tag}")

                    itemView1.setOnClickListener {
                        itemView3.setSelect(false)
                        itemView2.setSelect(false)
                        itemView1.setSelect(true)
                        selectIndex = 0
                        drawRoute()
                    }
                } else {
                    itemView1.visibility = View.INVISIBLE
                    itemView1.setOnClickListener(null)
                }
            }
        }

        plan = routePlan
        selectIndex = 0
        itemView1.setSelect(true)
        itemView2.setSelect(false)
        itemView3.setSelect(false)
        drawRoute()
    }

    fun clear() {
        polylineMap.forEach {
            it.value.remove()
        }

        polylineMap.clear()

        startMarker?.remove()
        startMarker = null

        endMarker?.remove()
        endMarker = null
    }


    private val polylineMap = mutableMapOf<Int, Polyline>()
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private fun drawRoute() {

        clear()

        plan?.apply {
            startMarker = tencentMap.addMarker(
                MarkerOptions(LatLng(startPoi.latitude, startPoi.longitude))
                    .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.drawable.app_icon_start_point)))
            )
            endMarker = tencentMap.addMarker(
                MarkerOptions(LatLng(endPoi.latitude, endPoi.longitude))
                    .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.drawable.app_icon_end_point)))
            )
        }

        val builder = LatLngBounds.Builder()
        plan?.routeDatas?.forEachIndexed { index, routeData ->

            var zIndex = 100
            val indexes = intArrayOf(0, routeData.routePoints.size)
            var colors = intArrayOf(ROUTE_COLOR_BACKUP, ROUTE_COLOR_BACKUP)
            var borderColors = intArrayOf(ROUTE_COLOR_BACKUP_STROKE, ROUTE_COLOR_BACKUP_STROKE)
            if (index == selectIndex) {
                colors = intArrayOf(ROUTE_COLOR_MAIN, ROUTE_COLOR_MAIN)
                borderColors = intArrayOf(ROUTE_COLOR_MAIN_STROKE, ROUTE_COLOR_MAIN_STROKE)
                zIndex = 200
            }

            builder.include(routeData.routePoints)

            val options = PolylineOptions()
            options.addAll(routeData.routePoints)
                .arrow(true)
                .arrowTexture(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.drawable.app_arrow_texture)))
                .color(Color.GREEN)
                .lineType(0)
                .arrowSpacing(150)
                .zIndex(zIndex)
                .level(OverlayLevel.OverlayLevelAboveBuildings)
                .width(32f)
                .clickable(true)
                .borderWidth(4f)
                .borderColors(borderColors)
                .colors(colors, indexes)

            polylineMap[index] = tencentMap.addPolyline(options)
        }

        tencentMap.moveCamera(CameraUpdateFactory.newLatLngBoundsRect(builder.build(),
            DP_20,
            DP_20,
            DP_20 + itemView1.height,
            DP_20
        ))
    }
}