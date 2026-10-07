package com.example.photocad.report

/** Values are a per-report snapshot. They are not edits to the site or its drawing. */
data class AlbumCover(
    val organizationName: String = "",
    val organizationAddress: String = "",
    val phone: String = "",
    val email: String = "",
    val inn: String = "",
    val kpp: String = "",
    val ogrn: String = "",
    val customer: String = "",
    val objectName: String = "",
    val objectAddress: String = "",
    val city: String = "",
    val year: String = "",
    val albumNumber: String = ""
)

data class PhotoAlbumInput(val cover: AlbumCover, val photos: List<PdfPhoto>) {
    val pageCount: Int get() = photos.size + 2
}
