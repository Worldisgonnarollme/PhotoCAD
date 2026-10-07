# Photo album design

The album is an A4 PDF for a drawing: an editable cover, a fixed text page, then one page per selected photograph with a point centered in a drawing excerpt. The simple report remains available. Cover edits and report captions belong to the current report session; only an explicit description save writes a photo description to Room.

`ReportViewModel` owns the wizard, the selected photo snapshot, generation, saving, and the post-save archive decision. Renderers receive immutable input and never query or mutate Room. `PhotoAlbumGenerator` coordinates three focused page renderers. The existing image loader, drawing document reader, temporary PDF cache, and Storage Access Framework export remain in use.

Room 6 already stores `Point.colorIndex`, which selects one of six existing colors. A shared integer ARGB palette will make the UI and PDF agree without changing stored indices. Room 7 adds `Point.isArchived` with default false through an additive migration. The working drawing and new report draft use active points; deletion code can still access all points. Archive action is available only after successful PDF export. In a Room transaction, each used point is archived only if every photo currently attached to it was included in the saved album.

At draft creation, a nonblank `Photo.description` wins; otherwise `Point.comment` becomes the temporary caption. The PDF receives this final edited caption without resolving it again. Missing captions are shown in the wizard and block generation until reviewed or filled. For drawings, the point coordinates are normalized to the source page. A bounded, aspect-preserving crop is centered near the point and clamped at edges; a marker uses the same crop transform.

Available references are two PNG captures of the cover and general information page. The original PDF and interface concept were not present in the attachment directory. The cover follows the provided visual structure; the fixed information copy lives in a separate resource and is drawn as PDF text.
