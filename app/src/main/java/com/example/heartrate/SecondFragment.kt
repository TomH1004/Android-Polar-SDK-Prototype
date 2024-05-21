package com.example.heartrate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.heartrate.databinding.FragmentSecondBinding
import com.google.android.material.snackbar.Snackbar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.UUID
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers


class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private val PERMISSION_REQUEST_CODE = 1
    private val disposables = CompositeDisposable()
    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var connectedDeviceId: String? = null

    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            requireContext(),
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION
            )
        ).apply {
            setApiCallback(object : PolarBleApiCallback() {
                override fun blePowerStateChanged(powered: Boolean) {
                    Log.d("Polar", "BLE power: $powered")
                }

                override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                    Log.d("Polar", "CONNECTED: ${polarDeviceInfo.deviceId}")
                    connectedDeviceId = polarDeviceInfo.deviceId
                    showSnackbar("Device connected: ${polarDeviceInfo.deviceId}")
                }

                override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                    Log.d("Polar", "CONNECTING: ${polarDeviceInfo.deviceId}")
                }

                override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                    Log.d("Polar", "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                    connectedDeviceId = null
                    showSnackbar("Device disconnected: ${polarDeviceInfo.deviceId}")
                }

                override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                    Log.d("Polar", "DIS INFO uuid: $uuid value: $value")
                }

                override fun batteryLevelReceived(identifier: String, level: Int) {
                    Log.d("Polar", "BATTERY LEVEL: $level")
                    binding.textBattery.text = "Battery Level: $level%"
                }
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()

        val connectButton: Button = binding.button
        connectButton.setOnClickListener {
            val deviceId = binding.editTextNumber.text.toString()
            if (deviceId.isNotBlank()) {
                connectToDevice(deviceId)
            } else {
                showSnackbar("Please enter a valid device ID")
            }
        }

        val hrStreamButton: Button = binding.buttonHrStream
        hrStreamButton.setOnClickListener {
            connectedDeviceId?.let {
                startHrStreaming(it)
            } ?: showSnackbar("No device connected")
        }

        val ecgStreamButton: Button = binding.buttonEcgStream
        ecgStreamButton.setOnClickListener {
            connectedDeviceId?.let {
                streamECG()
            } ?: showSnackbar("No device connected")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
        _binding = null
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
                showSnackbar("Permissions not granted")
            } else {
                Log.d("Polar", "Permissions granted")
            }
        }
    }

    private fun connectToDevice(deviceId: String) {
        try {
            api.connectToDevice(deviceId)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            Log.e("Polar", "Failed to connect. Reason: $polarInvalidArgument")
            showSnackbar("Failed to connect to device: $deviceId")
        }
    }

    private fun startHrStreaming(identifier: String) {
        hrDisposable?.dispose() // Dispose any existing HR stream
        hrDisposable = api.startHrStreaming(identifier).subscribe(
            { hrData: PolarHrData ->
                Log.d("Polar", "HR: ${hrData.samples}")
            },
            { throwable ->
                Log.e("Polar", "HR stream failed. Reason: $throwable")
                showSnackbar("Failed to start HR stream")
            }
        )
        disposables.add(hrDisposable!!)
    }

    private fun streamECG() {
        val isDisposed = ecgDisposable?.isDisposed ?: true
        if (isDisposed) {
            ecgDisposable = api.requestStreamSettings(connectedDeviceId!!, PolarBleApi.PolarDeviceDataType.ECG)
                .toFlowable()
                .flatMap { sensorSetting: PolarSensorSetting -> api.startEcgStreaming(connectedDeviceId!!, sensorSetting.maxSettings()) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarEcgData: PolarEcgData ->
                        Log.d("Polar", "ECG update")
                        for (data in polarEcgData.samples) {
                            // Handle the ECG data, e.g., plot it or log it
                            Log.d("Polar", "ECG: ${data.voltage.toFloat() / 1000.0}")
                        }
                    },
                    { error: Throwable ->
                        Log.e("Polar", "ECG stream failed $error")
                        ecgDisposable = null
                    },
                    {
                        Log.d("Polar", "ECG stream complete")
                    }
                )
        } else {
            // Stops streaming if it is "running"
            ecgDisposable?.dispose()
            ecgDisposable = null
        }
    }




    private fun showSnackbar(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }
}
