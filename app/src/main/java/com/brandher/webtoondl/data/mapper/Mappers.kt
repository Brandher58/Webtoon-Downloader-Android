package com.brandher.webtoondl.data.mapper

import com.brandher.webtoondl.data.db.ChapterEntity
import com.brandher.webtoondl.data.db.PageEntity
import com.brandher.webtoondl.data.db.SeriesEntity
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.PageRef
import com.brandher.webtoondl.domain.model.QueueStatus
import com.brandher.webtoondl.domain.model.Series

import com.brandher.webtoondl.data.db.ReadingPositionEntity
import com.brandher.webtoondl.domain.model.ReadingPosition

fun ReadingPositionEntity.toDomain() = ReadingPosition(
    chapterId = chapterId,
    pageIndex = pageIndex,
    offsetPx = offsetPx,
    updatedAt = updatedAt,
)

fun Series.toEntity(addedAt: Long = System.currentTimeMillis()) = SeriesEntity(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    coverUrl = coverUrl,
    author = author,
    genre = genre,
    summary = summary,
    addedAt = addedAt,
)

fun SeriesEntity.toDomain() = Series(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    coverUrl = coverUrl,
    author = author,
    genre = genre,
    summary = summary,
)

fun Chapter.toEntity() = ChapterEntity(
    id = id,
    seriesId = seriesId,
    sourceId = sourceId,
    episodeNo = episodeNo,
    number = number,
    title = title,
    viewerUrl = viewerUrl,
    thumbUrl = thumbUrl,
    date = date,
    queueStatus = QueueStatus.NONE.name,
    pagesTotal = null,
    pagesDone = 0,
    error = null,
)

fun ChapterEntity.toDomain() = Chapter(
    id = id,
    seriesId = seriesId,
    sourceId = sourceId,
    episodeNo = episodeNo,
    number = number,
    title = title,
    viewerUrl = viewerUrl,
    thumbUrl = thumbUrl,
    date = date,
)

fun PageRef.toEntity(chapterId: String, fileName: String, status: String = "PENDING") = PageEntity(
    id = "$chapterId:$pageNo",
    chapterId = chapterId,
    pageNo = pageNo,
    url = url,
    fileName = fileName,
    status = status,
)