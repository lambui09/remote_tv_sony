package com.remotelg.remotelg.ui.remote.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.MainVM
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.FragmentTouchpadBinding
import com.remotelg.remotelg.lgcontrol.RemoteControlKey
import com.remotelg.remotelg.utils.mapTo
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import timber.log.Timber
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadFragment : Fragment(R.layout.fragment_touchpad) {
  private val binding by viewBinding<FragmentTouchpadBinding>()
  private val mainVM by viewModels<MainVM>(ownerProducer = { requireActivity() })
  private val disposables = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.tag("<<<").d("$this::onCreate")
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Timber.tag("<<<").d("$this::onViewCreated")

    initKeyRemote()
    handleTrackpad()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    disposables.clear()
    Timber.tag("<<<").d("$this::onDestroyView")
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.tag("<<<").d("$this::onDestroy")
  }

  private fun initKeyRemote() {
    arrayOf(
      binding.ivBack to RemoteControlKey.back,
      binding.ivExit to RemoteControlKey.exit,
      binding.ivSource to RemoteControlKey.source,
      binding.ivGuide to RemoteControlKey.guide,
    ).map { (view, key) -> view.clicks().mapTo(key) }
      .merge()
      .onEach { mainVM.sendKey(it) }
      .launchIn(viewLifecycleOwner.lifecycleScope)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun handleTrackpad() {
    val keyS = PublishSubject.create<ScrollOrMove>()

    keyS
      .doOnNext { Timber.tag(">>>>>").d("[1] $it") }
      .filter { abs(it.dx) >= 0.1 || abs(it.dy) >= 0.1 }
      .publish { shared ->
        Observable.mergeArray(
          shared.ofType<ScrollOrMove.Move>()
            .buffer(2)
            .map { list ->
              RemoteControlKey.move(
                list.sumOf { it.dx },
                list.sumOf { it.dy },
              )
            },
          shared.ofType<ScrollOrMove.Scroll>()
            .buffer(2)
            .map { list ->
              RemoteControlKey.scroll(
                list.sumOf { it.dx },
                list.sumOf { it.dy },
              )
            }
        )
      }
      .doOnNext { Timber.tag(">>>>>").d("[2] $it") }
      .subscribeBy(onNext = mainVM::sendKey)
      .addTo(disposables)

    val detector = GestureDetectorCompat(
      requireContext(),
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent?) = true

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
          Timber.d("Single tap")
          mainVM.sendKey(RemoteControlKey.clickTrackPad)
          return true
        }

        override fun onScroll(
          e1: MotionEvent,
          e2: MotionEvent,
          distanceX: Float,
          distanceY: Float
        ): Boolean {
          val dx = -distanceX.toDouble()
          val dy = -distanceY.toDouble()
          keyS.onNext(
            if (e2.pointerCount >= 2) ScrollOrMove.Scroll(dx, dy)
            else ScrollOrMove.Move(dx, dy)
          )

          return true
        }
      }
    )

    binding.trackpad.containerTrackpad.setOnTouchListener { _, motionEvent ->
      detector.onTouchEvent(
        motionEvent
      )
    }
  }
}

private sealed interface ScrollOrMove {
  val dx: Double
  val dy: Double

  data class Scroll(override val dx: Double, override val dy: Double) : ScrollOrMove
  data class Move(override val dx: Double, override val dy: Double) : ScrollOrMove
}
