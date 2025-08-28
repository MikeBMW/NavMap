package com.tencent.navix.demo.route

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.navix.api.model.NavDriveRoute
import com.tencent.navix.api.model.NavNonMotorRoute
import com.tencent.navix.api.model.NavRoute
import com.tencent.navix.demo.R
import java.util.*

class RouteItemView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val index: TextView
    private val name: TextView

    private var selectedOn: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_route_item, this)
        index = findViewById(R.id.route_index)
        name = findViewById(R.id.route_name)
    }

    fun update(data: NavRoute, tag: String) {
        index.text = tag
        if (data is NavDriveRoute) {
            name.text = "全长 ${distanceToString(data.distance)}"
        } else if (data is NavNonMotorRoute) {
            name.text = "全长 ${distanceToString(data.distance)}"
        }
    }

    fun setSelect(selected: Boolean) {
        this.selectedOn = selected
        if (selected) {
            findViewById<View>(R.id.route_group).setBackgroundResource(R.drawable.app_bg_focus_r18)

            index.setTextColor(Color.parseColor("#FFFFFF"))
            name.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            findViewById<View>(R.id.route_group).background = null

            index.setTextColor(Color.parseColor("#333333"))
            name.setTextColor(Color.parseColor("#333333"))
        }
    }


    fun distanceToString(distance: Int): String? {
        if (distance < 1000) {
            return distance.toString() + "米"
        }
        var disStr = String.format(
            Locale.getDefault(), "%.1f",
            distance.toDouble() / 1000
        )
        if (disStr.endsWith(".0") || distance > 1000000) {
            disStr = disStr.substring(0, disStr.length - 2)
        }
        return disStr + "公里"
    }

}