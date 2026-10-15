package com.example.photocad.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun <T> JSONArray.readItems(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
private fun <T> jsonArray(items: List<T>, block: (T) -> JSONObject): JSONArray = JSONArray().apply { items.forEach { put(block(it)) } }

fun ArchiveManifest.toJson(): String = JSONObject().apply {
    put("version", version)
    put("site", JSONObject().apply {
        put("name", site.name); put("address", site.address); put("description", site.description)
    })
    put("details", details?.let { d -> JSONObject().apply {
        put("organizationName", d.organizationName); put("organizationAddress", d.organizationAddress)
        put("phone", d.phone); put("email", d.email); put("inn", d.inn); put("kpp", d.kpp)
        put("ogrn", d.ogrn); put("customer", d.customer); put("city", d.city)
        put("year", d.year); put("albumNumber", d.albumNumber)
    } } ?: JSONObject.NULL)
    put("drawings", jsonArray(drawings) { d -> JSONObject().apply {
        put("id", d.id); put("name", d.name); put("description", d.description); put("file", d.file)
    } })
    put("points", jsonArray(points) { p -> JSONObject().apply {
        put("id", p.id); put("drawingId", p.drawingId); put("x", p.x.toDouble()); put("y", p.y.toDouble())
        put("pageNumber", p.pageNumber); put("displayNumber", p.displayNumber); put("comment", p.comment)
        put("colorIndex", p.colorIndex); put("isArchived", p.isArchived); put("isFixed", p.isFixed)
    } })
    put("photos", jsonArray(photos) { p -> JSONObject().apply {
        put("id", p.id); put("pointId", p.pointId); put("file", p.file)
        put("description", p.description ?: JSONObject.NULL); put("capturedAtLocal", p.capturedAtLocal ?: JSONObject.NULL)
        put("capturedOffsetMinutes", p.capturedOffsetMinutes ?: JSONObject.NULL)
    } })
    put("files", jsonArray(files) { f -> JSONObject().apply {
        put("path", f.path); put("size", f.size); put("sha256", f.sha256)
    } })
}.toString()

fun parseArchiveManifest(json: String): ArchiveManifest {
    val root = JSONObject(json)
    val s = root.getJSONObject("site")
    val d = if (root.isNull("details")) null else root.getJSONObject("details").let {
        ArchiveDetails(it.getString("organizationName"), it.getString("organizationAddress"), it.getString("phone"),
            it.getString("email"), it.getString("inn"), it.getString("kpp"), it.getString("ogrn"),
            it.getString("customer"), it.getString("city"), it.getString("year"), it.getString("albumNumber"))
    }
    val manifest = ArchiveManifest(
        root.getInt("version"), ArchiveSite(s.getString("name"), s.getString("address"), s.getString("description")), d,
        root.getJSONArray("drawings").readItems { ArchiveDrawing(it.getLong("id"), it.getString("name"), it.getString("description"), it.getString("file")) },
        root.getJSONArray("points").readItems { ArchivePoint(it.getLong("id"), it.getLong("drawingId"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.getInt("pageNumber"), it.getInt("displayNumber"), it.getString("comment"), it.getInt("colorIndex"), it.getBoolean("isArchived"), it.getBoolean("isFixed")) },
        root.getJSONArray("photos").readItems { ArchivePhoto(it.getLong("id"), it.getLong("pointId"), it.getString("file"), it.optionalString("description"), it.optionalString("capturedAtLocal"), it.optionalInt("capturedOffsetMinutes")) },
        root.getJSONArray("files").readItems { ArchiveFile(it.getString("path"), it.getLong("size"), it.getString("sha256")) }
    )
    validateArchiveManifest(manifest)
    return manifest
}
