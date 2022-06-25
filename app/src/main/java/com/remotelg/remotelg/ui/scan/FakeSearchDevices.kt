package com.remotelg.remotelg.ui.scan

import com.connectsdk.device.ConnectableDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeSearchDevices @Inject constructor() : SearchDevices {
  override fun invoke(type: SearchDevices.Type): Flow<List<ConnectableDevice>> {
    return flow {
      delay(2_000)

      (1..3).forEach { count ->
        emit(
          (1..count).map { i ->
            ConnectableDevice(
              "192.168.0.$i",
              "Device $i",
              "Model name $i",
              "Model number $i"
            )
          }
        )

        delay(2_000)
      }
    }
  }
}
