package com.remotelg.remotelg.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Projects each source value to a [Flow] which is merged in the output [Flow] only if the previous projected [Flow] has completed.
 * If value is received while there is some projected [Flow] sequence being merged it will simply be ignored.
 *
 * This method is a shortcut for `map(transform).flattenFirst()`. See [flattenFirst].
 *
 * ### Operator fusion
 *
 * Applications of [flowOn], [buffer], and [produceIn] _after_ this operator are fused with
 * its concurrent merging so that only one properly configured channel is used for execution of merging logic.
 *
 * @param transform A transform function to apply to value that was observed while no Flow is executing in parallel.
 */
@ExperimentalCoroutinesApi
public fun <T, R> Flow<T>.flatMapFirst(transform: suspend (value: T) -> Flow<R>): Flow<R> =
  map(transform).flattenFirst()

/**
 * Converts a higher-order [Flow] into a first-order [Flow] by dropping inner [Flow] while the previous inner [Flow] has not yet completed.
 */
@ExperimentalCoroutinesApi
public fun <T> Flow<Flow<T>>.flattenFirst(): Flow<T> = channelFlow {
  val outerScope = this
  val busy = AtomicBoolean(false)

  collect { inner ->
    if (busy.compareAndSet(false, true)) {
      // Do not pay for dispatch here, it's never necessary
      launch(start = CoroutineStart.UNDISPATCHED) {
        try {
          inner.collect { outerScope.send(it) }
          busy.set(false)
        } catch (e: CancellationException) {
          // cancel outer scope on cancellation exception, too
          outerScope.cancel(e)
        }
      }
    }
  }
}

/**
 * Emits the given constant value on the output Flow every time the source Flow emits a value.
 *
 * @param value The value to map each source value to.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <T, R> Flow<T>.mapTo(value: R): Flow<R> =
  transform { return@transform emit(value) }
