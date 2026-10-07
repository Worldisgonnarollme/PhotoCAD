package com.example.photocad.report

import com.example.photocad.data.Site
import com.example.photocad.data.SiteReportDetails

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

fun albumCoverForSite(site: Site?, drawingName: String, details: SiteReportDetails?, currentYear: String): AlbumCover =
    AlbumCover(
        organizationName = details?.organizationName.orEmpty(),
        organizationAddress = details?.organizationAddress.orEmpty(),
        phone = details?.phone.orEmpty(), email = details?.email.orEmpty(),
        inn = details?.inn.orEmpty(), kpp = details?.kpp.orEmpty(), ogrn = details?.ogrn.orEmpty(),
        customer = details?.customer.orEmpty(),
        objectName = site?.name ?: drawingName,
        objectAddress = site?.address.orEmpty(),
        city = details?.city.orEmpty(),
        year = details?.year?.takeIf { it.isNotBlank() } ?: currentYear,
        albumNumber = details?.albumNumber.orEmpty()
    )
