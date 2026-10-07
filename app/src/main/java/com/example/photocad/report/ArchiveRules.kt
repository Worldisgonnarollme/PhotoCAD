package com.example.photocad.report

/** Called with photo ids read from Room after the PDF has been saved. */
fun eligibleArchivePointIds(
    attachedPhotoIdsByPoint: Map<Long, Set<Long>>,
    includedPhotoIds: Set<Long>
): Set<Long> = attachedPhotoIdsByPoint.filterValues { ids ->
    ids.isNotEmpty() && ids.all(includedPhotoIds::contains)
}.keys
