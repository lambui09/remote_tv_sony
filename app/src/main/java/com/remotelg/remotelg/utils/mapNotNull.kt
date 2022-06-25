package com.remotelg.remotelg.utils

import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.internal.disposables.DisposableHelper

@CheckResult
fun <T : Any, R : Any> Observable<T>.mapNotNull(transform: (T) -> R?): Observable<R> =
  lift { MapNotNullObserver(it, transform) }

private class MapNotNullObserver<T : Any, R : Any>(
  private val downstream: Observer<R>,
  private val transform: (T) -> R?
) : Observer<T>, Disposable {
  private var upstream: Disposable? = null

  override fun onSubscribe(d: Disposable) {
    if (DisposableHelper.validate(upstream, d)) {
      upstream = d
      downstream.onSubscribe(this)
    }
  }

  override fun onNext(t: T) = transform(t)?.let(downstream::onNext).unit

  override fun onError(e: Throwable) = downstream.onError(e)

  override fun onComplete() = downstream.onComplete()

  override fun dispose() = upstream!!.dispose()

  override fun isDisposed(): Boolean = upstream!!.isDisposed
}
