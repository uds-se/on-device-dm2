package org.droidmate.accessibility

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.GET_META_DATA
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppAdapter(context: Context) : BaseAdapter() {
    private val layoutInflater = LayoutInflater.from(context)
    private val appList: List<App> by lazy {
        val packageManager = context.packageManager
        val packages = packageManager.getInstalledPackages(GET_META_DATA)
        packages
            .filterNot { it.isSystemPackage() || it.isMyself() || it.isDMLauncher() }
            .map {
                App(
                    it.applicationInfo.loadLabel(packageManager).toString(),
                    it.applicationInfo.packageName,
                    it.applicationInfo.loadIcon(packageManager)
                )
            }
    }

    private fun PackageInfo.isSystemPackage(): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }

    private fun PackageInfo.isDMLauncher(): Boolean {
        return applicationInfo.packageName.startsWith("com.example.dm2launcher")
    }

    private fun PackageInfo.isMyself(): Boolean {
        val packageName = MainActivity::class.java.`package`?.name
            ?: throw IllegalStateException("Unable to determine DM-2 package")
        return applicationInfo.packageName.startsWith(packageName)
    }

    override fun getCount(): Int {
        return appList.size
    }

    override fun getItem(id: Int): App {
        return if (count > id) {
            appList[id]
        } else {
            appList.last()
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View? {
        val view: View?
        val viewHolder: ViewHolder?

        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.installed_app_list, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        viewHolder.appName.text = appList[position].name
        viewHolder.appPackage.text = appList[position].packageName
        viewHolder.appIcon.setImageDrawable(appList[position].icon)
        return view
    }

    internal inner class ViewHolder(row: View) {
        var appName: TextView = row.findViewById<View>(R.id.app_name) as TextView
        var appPackage: TextView = row.findViewById<View>(R.id.app_package) as TextView
        var appIcon: ImageView = row.findViewById<View>(R.id.app_icon) as ImageView
    }
}
