package com.remotelg.remotelg.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.EmptyCoroutineContext

inline fun <T : Any> LiveData<Event<T>>.observeEvent(
  owner: LifecycleOwner,
  crossinline observer: (T) -> Unit,
) = Observer { event: Event<T>? ->
  event?.getContentIfNotHandled()?.let(observer)
}.also { observe(owner, it) }

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> LiveData<T>.toFlow(): Flow<T?> = callbackFlow {
  val observer = Observer<T> { trySend(it) }.also(::observeForever)

  awaitClose {
    Dispatchers.Main.immediate.dispatch(EmptyCoroutineContext) {
      removeObserver(observer)
    }
  }
}.flowOn(Dispatchers.Main.immediate)

fun <A, B, C, R> LiveData<A>.combineLatest(
  b: LiveData<B>,
  c: LiveData<C>,
  combine: (A, B, C) -> R,
): LiveData<R> {
  return MediatorLiveData<R>().apply {
    var lastA: A? = null
    var lastB: B? = null
    var lastC: C? = null

    addSource(this@combineLatest) { v ->
      if (v == null && value != null) value = null
      lastA = v

      lastA?.let { a ->
        lastB?.let { b ->
          lastC?.let { value = combine(a, b, it) }
        }
      }
    }

    addSource(b) { v ->
      if (v == null && value != null) value = null
      lastB = v

      lastA?.let { a ->
        lastB?.let { b ->
          lastC?.let { value = combine(a, b, it) }
        }
      }
    }

    addSource(c) { v ->
      if (v == null && value != null) value = null
      lastC = v

      lastA?.let { a ->
        lastB?.let { b ->
          lastC?.let { value = combine(a, b, it) }
        }
      }
    }
  }
}
