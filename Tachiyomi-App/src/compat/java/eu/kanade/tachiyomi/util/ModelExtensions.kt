package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/* From: eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader */

private val chapterCache: ChapterCache get() = Injekt.get()

/**
 * Returns an observable of the page with the downloaded image.
 *
 * @param page the page whose source image has to be downloaded.
 */
fun HttpSource.fetchImageFromCacheThenNet(page: ReaderPage): Observable<ReaderPage> {
    return if (page.imageUrl.isNullOrEmpty())
        getImageUrl(page).flatMap { getCachedImage(it) }
    else
        getCachedImage(page)
}

private fun HttpSource.getImageUrl(page: ReaderPage): Observable<ReaderPage> {
    page.status = Page.LOAD_PAGE
    return fetchImageUrl(page)
            .doOnError { page.status = Page.ERROR }
            .onErrorReturn { null }
            .doOnNext { page.imageUrl = it }
            .map { page }
}

/**
 * Returns an observable of the page that gets the image from the chapter or fallbacks to
 * network and copies it to the cache calling [cacheImage].
 *
 * @param page the page.
 */
private fun HttpSource.getCachedImage(page: ReaderPage): Observable<ReaderPage> {
    val imageUrl = page.imageUrl ?: return Observable.just(page)

    return Observable.just(page)
            .flatMap {
                if (!chapterCache.isImageInCache(imageUrl)) {
                    cacheImage(page)
                } else {
                    Observable.just(page)
                }
            }
            .doOnNext {
                page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
                page.status = Page.READY
            }
            .doOnError { page.status = Page.ERROR }
            .onErrorReturn { page }
}

/**
 * Returns an observable of the page that downloads the image to [ChapterCache].
 *
 * @param page the page.
 */
private fun HttpSource.cacheImage(page: ReaderPage): Observable<ReaderPage> {
    page.status = Page.DOWNLOAD_IMAGE
    return fetchImage(page)
            .doOnNext { chapterCache.putImageToCache(page.imageUrl!!, it) }
            .map { page }
}

