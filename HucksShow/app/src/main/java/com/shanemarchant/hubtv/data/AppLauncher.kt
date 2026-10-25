package com.shanemarchant.hubtv.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

object AppLauncher {
    @Suppress("DEPRECATION")
    fun installed(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || pkg in result) continue
                result[pkg] = AppInfo(pkg, ri.loadLabel(pm).toString())
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String): Boolean = try {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent == null) false else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        }
    } catch (e: Exception) {
        false
    }

    fun icon(ctx: Context, pkg: String): Drawable? =
        try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun norm(s: String) = s.lowercase()
        .replace("+", "plus")
        .replace(Regex("[^a-z0-9]"), "")
        .replace("standardwithads", "")
        .replace("basicwithads", "")
        .replace("withads", "")

    /** Matches a TMDB service name ("Amazon Prime Video") to an installed app ("Prime Video"). */
    fun match(provider: String, apps: List<AppInfo>): AppInfo? {
        val p = norm(provider)
        if (p.length < 3) return null
        apps.firstOrNull { norm(it.label) == p }?.let { return it }
        return apps.firstOrNull {
            val a = norm(it.label)
            a.length >= 3 && (p.contains(a) || a.contains(p))
        }
    }
}
