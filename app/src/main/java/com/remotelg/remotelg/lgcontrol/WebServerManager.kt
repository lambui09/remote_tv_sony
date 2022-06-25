package com.remotelg.remotelg.lgcontrol

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.MainThread
import androidx.core.database.getLongOrNull
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.remotelg.remotelg.di.AppCoroutinesScope
import com.remotelg.remotelg.ipAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebServerManager @Inject constructor(
  @ApplicationContext private val appContext: Context,
  @AppCoroutinesScope private val coroutineScope: CoroutineScope,
) {

  @Volatile
  private var webServer: WebServer? = null
  private val mutex = Mutex()
  private var serverJob: Job? = null

  @MainThread
  fun startWebServerIfNeeded() {
    appContext.ipAddress()

    val ws = webServer

    Timber.tag("^^^").d("CHECK $ws ${ws?.isAlive}")

    ws?.run {
      if (isAlive) {
        return
      } else {
        stop()
        webServer = null
      }
    }

    serverJob = coroutineScope.launch(Dispatchers.IO) {
      Timber.tag("^^^").d("BEFORE WITHLOCK $ws $webServer")

      mutex.withLock {
        Timber.tag("^^^").d("START WITHLOCK $ws $webServer")

        if (ws !== webServer) {
          return@launch
        }

        ws?.run {
          if (isAlive) {
            return@launch
          } else {
            stop()
            webServer = null
          }
        }

        yield()

        webServer = WebServer(PORT, appContext).run {
          runCatching {
            start()

            delay(1000)
            Timber
              .tag("^^^")
              .d(">>>>>>>>>>. START WEBSERVER SUCCESS $hostname:$listeningPort - ${wasStarted()} $isAlive")

            this
          }.onFailure {
            stop()

            yield()

            Timber
              .tag("^^^")
              .d(">>>>>>>>>>. START WEBSERVER FAILURE $it $hostname:$listeningPort - ${wasStarted()} $isAlive")
          }.getOrNull()
        }
      }
    }
  }

  @MainThread
  fun stopWebServer() {
    serverJob?.cancel()
    serverJob = null

    webServer?.stop()
    webServer = null

    Timber.tag("^^^").d("STOP SERVER")
  }

  companion object {
    const val PORT = 9687
  }
}

internal fun Uri.length(context: Context): Long {
  val fsLength = runCatching {
    context.contentResolver
      .openAssetFileDescriptor(this, "r")
      ?.use { it.length }
  }.getOrNull() ?: AssetFileDescriptor.UNKNOWN_LENGTH

  if (fsLength != AssetFileDescriptor.UNKNOWN_LENGTH) return fsLength

  // if "content://" uri scheme, try contentResolver table
  return if (scheme == ContentResolver.SCHEME_CONTENT) {
    runCatching {
      context.contentResolver.query(
        this,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null
      )
    }
      .mapCatching { cursor ->
        cursor?.use {
          it.getLongOrNull(it.getColumnIndex(OpenableColumns.SIZE))
        } ?: AssetFileDescriptor.UNKNOWN_LENGTH
      }
      .getOrElse { AssetFileDescriptor.UNKNOWN_LENGTH }
  } else {
    AssetFileDescriptor.UNKNOWN_LENGTH
  }
}

internal class WebServer(
  port: Int,
  private val appContext: Context,
) : NanoHTTPD(port) {
  override fun serve(session: IHTTPSession): Response = runCatching {
    val uri = session.parameters?.get("uri")?.firstOrNull()
    val mimeType = session.parameters?.get("mimeType")?.firstOrNull()

    Timber.tag("^^^").d("[LOG] --> ${session.method} ${session.uri}")
    Timber.tag("^^^").d("$mimeType $uri")
    Firebase.crashlytics.run {
      log("WebServer $mimeType $uri ${session.headers?.get("range")} -> ${appContext.ipAddress()}")
      recordException(Exception("NOTHING :)"))
    }

    when (session.method) {
      Method.HEAD -> {
        if (uri != null) {
          newFixedLengthResponse(
            Response.Status.OK,
            session.headers.getOrElse("Content-Type") { "text/html" },
            "",
          ).apply {
            addHeader(CONTENT_LENGTH_HEADER, getContentLength(uri).toString())
          }
        } else {
          newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            MIME_PLAINTEXT,
            "uri must be not null!",
          )
        }
      }
      Method.GET -> {
        when {
          uri == null || mimeType == null -> newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            MIME_PLAINTEXT,
            "uri and mimeType must be not null!",
          )
          AUDIO_OR_VIDEO_REGEX.matches(mimeType) -> getVideoResponse(
            uri,
            mimeType,
            session.headers?.get("range"),
          )
          else -> getDefaultResponse(
            uri,
            mimeType,
          )
        }
      }
      else -> newFixedLengthResponse(
        Response.Status.METHOD_NOT_ALLOWED,
        MIME_PLAINTEXT,
        "HTTPError: HTTP 405: Method Not Allowed",
      )
    }
  }.getOrElse {
    internalServerError()
  }.also {
    Timber.tag("^^^")
      .d(
        "[LOG] <- ${session.method} ${session.uri} - Done ${session.uri} ${it.status} ${it.mimeType} ${it.data}" +
          " # ${it.getHeader(CONTENT_LENGTH_HEADER)} # ${it.getHeader(CONTENT_RANGE_HEADER)}"
      )
    Firebase.crashlytics.run {
      log(
        "WebServer DONE ${session.uri} ${it.status} # ${it.mimeType} # ${it.data} # ${
        it.getHeader(
          CONTENT_LENGTH_HEADER
        )
        } # ${it.getHeader(CONTENT_RANGE_HEADER)}"
      )
      recordException(Exception("NOTHING <3"))
    }
  }

  private fun getContentLength(uriString: String): Long? {
    return Uri
      .parse(uriString)
      .let { uri ->
        uri to runCatching { appContext.contentResolver.openInputStream(uri)!! }
          .getOrElse { return null }
      }
      .let { (uri, inputStream) ->
        uri.length(appContext).takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH }
          ?: inputStream
            .buffered()
            .use { it.readBytes() }
            .size
            .toLong()
      }
  }

  private fun getDefaultResponse(uriString: String, mimeType: String): Response {
    return Uri.parse(uriString)
      .let { uri ->
        uri to runCatching { appContext.contentResolver.openInputStream(uri)!! }
          .getOrElse { return internalServerError() }
      }
      .let { (uri, inputStream) ->
        uri
          .length(appContext)
          .takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH }
          ?.let {
            newFixedLengthResponse(
              Response.Status.OK,
              mimeType,
              inputStream,
              it,
            )
          }
          ?: inputStream
            .buffered()
            .use { it.readBytes() }
            .let {
              newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                ByteArrayInputStream(it),
                it.size.toLong(),
              )
            }
      }
  }

  private fun getVideoResponse(uriString: String, mimeType: String, range: String?): Response {
    Timber.tag("^^^").d("range=$range")

    range ?: return getDefaultResponse(uriString, mimeType)

    if (!range.startsWith(RANGE_UNIT)) {
      return newFixedLengthResponse(
        Response.Status.BAD_REQUEST,
        MIME_HTML,
        "Range header invalid",
      )
    }

    val r = range.trim().replace(RANGE_UNIT, "")
    val positions = r.split(RANGE_DELIMITER).mapNotNull { it.trim().toLongOrNull() }
    Timber.tag("^^^").d("positions=$positions")

    return when (positions.size) {
      1 -> when {
        r.startsWith(RANGE_DELIMITER) -> {
          // -LAST
          // (it - 1) - (it - last) + 1 === last
          positions[0].let { last ->
            getVideoResponseWithStartEnd(
              uriString = uriString,
              mimeType = mimeType,
              rangeHeader = range,
              startProducer = { it - last },
              endProducer = { it - 1 },
            )
          }
        }
        r.endsWith(RANGE_DELIMITER) -> {
          // START-
          positions[0].let { start ->
            getVideoResponseWithStartEnd(
              uriString = uriString,
              mimeType = mimeType,
              rangeHeader = range,
              startProducer = { start },
              endProducer = { it - 1 },
            )
          }
        }
        else -> rangeNotSatisfiable(range)
      }
      2 -> {
        val (start, end) = positions
        getVideoResponseWithStartEnd(
          uriString = uriString,
          mimeType = mimeType,
          rangeHeader = range,
          startProducer = { start },
          endProducer = { end },
        )
      }
      else -> rangeNotSatisfiable(range)
    }
  }

  private fun getVideoResponseWithStartEnd(
    uriString: String,
    mimeType: String,
    rangeHeader: String,
    startProducer: (Long) -> Long,
    endProducer: (Long) -> Long,
  ): Response {

    val result = { fileLength: Long, stream: InputStream ->
      val start = startProducer(fileLength).coerceAtLeast(0)
      val end = endProducer(fileLength).coerceAtMost(fileLength - 1)
      Timber.tag("^^^").d("start=$start, end=$end, ${start <= end}")

      if (start <= end) {
        stream.skip(start)

        val contentLength = (end - start + 1).coerceAtLeast(0)
        newFixedLengthResponse(
          Response.Status.PARTIAL_CONTENT,
          mimeType,
          stream,
          contentLength,
        ).apply {
          addHeader(ACCEPT_RANGE_HEADER, "bytes")
          addHeader(CONTENT_LENGTH_HEADER, contentLength.toString())
          addHeader(CONTENT_RANGE_HEADER, "bytes $start-$end/$fileLength")
        }
      } else {
        rangeNotSatisfiable(rangeHeader)
      }
    }

    return Uri
      .parse(uriString)
      .let { uri ->
        uri to runCatching { appContext.contentResolver.openInputStream(uri)!! }
          .getOrElse { return internalServerError() }
      }
      .let { (uri, inputStream) ->
        uri.length(appContext)
          .takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH }
          ?.let { result(it, inputStream) }
          ?: inputStream
            .buffered()
            .use { it.readBytes() }
            .let { result(it.size.toLong(), ByteArrayInputStream(it)) }
      }
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun internalServerError() = newFixedLengthResponse(
    Response.Status.INTERNAL_ERROR,
    MIME_PLAINTEXT,
    "HTTPError: HTTP 500: Internal Server Error",
  )

  @Suppress("NOTHING_TO_INLINE")
  private inline fun rangeNotSatisfiable(rangeHeader: String) = newFixedLengthResponse(
    Response.Status.RANGE_NOT_SATISFIABLE,
    MIME_PLAINTEXT,
    rangeHeader,
  )

  private companion object {
    val AUDIO_OR_VIDEO_REGEX = """(audio|video)/(.+)""".toRegex()
    private const val RANGE_UNIT = "bytes="
    private const val RANGE_DELIMITER = '-'
    private const val ACCEPT_RANGE_HEADER = "Accept-Ranges"
    private const val CONTENT_LENGTH_HEADER = "Content-Length"
    private const val CONTENT_RANGE_HEADER = "Content-Range"
  }
}
