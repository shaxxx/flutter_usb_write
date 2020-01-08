package hr.integrator.flutter_usb_write;

import androidx.annotation.NonNull;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel.EventSink;

/**
 * FlutterUsbWritePlugin
 */
public class FlutterUsbWritePlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

  private final String TAG = FlutterUsbWritePlugin.class.getSimpleName();

  private UsbManager m_Manager;
  private Context applicationContext;
  private MethodChannel methodChannel;
  private EventChannel eventChannel;
  private BroadcastReceiver usbStateChangeReceiver;
  private UsbEndpoint ep;
  private UsbInterface mInterface;
  private UsbDeviceConnection m_Connection;

  private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
  private static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
  private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

  public static void registerWith(Registrar registrar) {
    final FlutterUsbWritePlugin instance = new FlutterUsbWritePlugin();
    instance.onAttachedToEngine(registrar.context(), registrar.messenger());
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
  }

  private void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
    this.applicationContext = applicationContext;
    m_Manager = (UsbManager) applicationContext.getSystemService(android.content.Context.USB_SERVICE);
    methodChannel = new MethodChannel(messenger, "flutter_usb_write/methods");
    eventChannel = new EventChannel(messenger, "flutter_usb_write/events");
    eventChannel.setStreamHandler(this);
    methodChannel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    applicationContext = null;
    methodChannel.setMethodCallHandler(null);
    methodChannel = null;
    eventChannel.setStreamHandler(null);
    eventChannel = null;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
    case "listDevices":
      listDevices(result);
      break;
    case "open":
      int vid = (int) (call.argument("vid") == null ? 0 : call.argument("vid"));
      int pid = (int) (call.argument("pid") == null ? 0 : call.argument("pid"));
      int deviceId = (int) (call.argument("deviceId") == null ? 0 : call.argument("deviceId"));
      open(vid, pid, deviceId, result);
      break;
    case "write":
      write((byte[]) call.argument("bytes"), result);
      break;
    case "close":
      close();
      result.success(true);
      break;
    case "controlTransfer":
      int requestType = (int) (call.argument("requestType") == null ? 0 : call.argument("requestType"));
      int request = (int) (call.argument("request") == null ? 0 : call.argument("request"));
      int value = (int) (call.argument("value") == null ? 0 : call.argument("value"));
      int index = (int) (call.argument("index") == null ? 0 : call.argument("index"));
      byte[] buffer = call.argument("bytes");
      int length = (int) (call.argument("length") == null ? 0 : call.argument("length"));
      int timeout = (int) (call.argument("timeout") == null ? 0 : call.argument("timeout"));
      int callResult = setControlCommand(requestType, request, value, index, buffer, length, timeout);
      result.success(callResult);
      break;
    default:
      result.notImplemented();
      break;
    }
  }

  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    usbStateChangeReceiver = createUsbStateChangeReceiver(events);
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_USB_DETACHED);
    filter.addAction(ACTION_USB_ATTACHED);
    applicationContext.registerReceiver(usbStateChangeReceiver, filter);
  }

  @Override
  public void onCancel(Object arguments) {
    applicationContext.unregisterReceiver(usbStateChangeReceiver);
    usbStateChangeReceiver = null;
  }

  public synchronized boolean hasPermission(UsbDevice device) {
    return this.m_Manager.hasPermission(device);
  }

  public synchronized boolean hasUsbHostFeature() {
    return applicationContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
  }

  private void write(final byte[] bytes, final Result result) {
    if (bytes != null) {
      int transferResult = -1;
      if (this.ep != null && this.mInterface != null && this.m_Connection != null) {
        transferResult = this.m_Connection.bulkTransfer(this.ep, bytes, bytes.length, 0);
      } else {
        if (this.m_Connection.claimInterface(this.mInterface, true)) {
          transferResult = this.m_Connection.bulkTransfer(this.ep, bytes, bytes.length, 0);
        }
      }
      result.success(transferResult >= 0);
      return;
    }
    result.success(true);
  }

  private void openDevice(UsbDevice device, boolean allowAcquirePermission, final OpenDeviceCallback openDeviceCb) {

    final AcquirePermissionCallback cb = new AcquirePermissionCallback() {

      @Override
      public void onSuccess(UsbDevice device) {
        openDevice(device, false, openDeviceCb);
      }

      @Override
      public void onFailed(UsbDevice device) {
        openDeviceCb.onFailed(device, "PERMISSION_ERROR", "Failed to acquire permissions.");
      }
    };

    try {
      this.close();
      m_Connection = m_Manager.openDevice(device);
      if (m_Connection == null && allowAcquirePermission) {
        acquirePermissions(device, cb);
        return;
      }

      if (device.getInterfaceCount() == 0) {
        openDeviceCb.onFailed(device, "INTERFACE_NOT_FOUND_ERROR", "USB Interface not found.");
        return;
      }

      this.mInterface = device.getInterface(0);
      if (this.mInterface == null) {
        openDeviceCb.onFailed(device, "INTERFACE_NOT_FOUND_ERROR", "USB Interface not found.");
        return;
      }

      if (this.mInterface.getEndpointCount() == 0) {
        openDeviceCb.onFailed(device, "ENDPOINT_NOT_FOUND_ERROR", "USB Endpoint not found.");
        return;
      }

      for (int i = 0; i < this.mInterface.getEndpointCount(); ++i) {
        if (this.mInterface.getEndpoint(i).getType() == 2 && this.mInterface.getEndpoint(i).getDirection() != 128) {
          this.ep = this.mInterface.getEndpoint(i);
        }
      }

      if (this.ep == null) {
        openDeviceCb.onFailed(device, "ENDPOINT_NOT_FOUND_ERROR", "USB Endpoint not found.");
        return;
      }
      openDeviceCb.onSuccess(device);
    } catch (java.lang.SecurityException e) {

      if (allowAcquirePermission) {
        acquirePermissions(device, cb);
        return;
      } else {
        openDeviceCb.onFailed(device, "PERMISSION_ERROR", "Failed to acquire permissions.");
      }
    }
  }

  public synchronized void close() {
    if (this.m_Connection != null) {
      this.m_Connection.close();
      this.ep = null;
      this.mInterface = null;
      this.m_Connection = null;
    }
  }

  private UsbDevice getDevice(int vid, int pid, int deviceId) {
    Map<String, UsbDevice> devices = m_Manager.getDeviceList();
    for (UsbDevice device : devices.values()) {
      if (deviceId == device.getDeviceId() || (device.getVendorId() == vid && device.getProductId() == pid)) {
        return device;
      }
    }
    return null;
  }

  private void open(int vid, int pid, int deviceId, final Result result) {
    UsbDevice device = getDevice(vid, pid, deviceId);
    if (device != null) {
      final OpenDeviceCallback cb = new OpenDeviceCallback() {

        @Override
        public void onSuccess(UsbDevice device) {
          result.success(serializeDevice(device));
        }

        @Override
        public void onFailed(UsbDevice device, String errorCode, String error) {
          result.error(errorCode, error, null);
        }
      };
      this.openDevice(device, true, cb);
      return;
    }
    result.error("DEVICE_NOT_FOUND_ERROR", "No such device", null);
  }

  private HashMap<String, Object> serializeDevice(UsbDevice device) {
    HashMap<String, Object> dev = new HashMap<>();
    dev.put("vid", device.getVendorId());
    dev.put("pid", device.getProductId());
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      dev.put("manufacturerName", device.getManufacturerName());
      dev.put("productName", device.getProductName());
      dev.put("serialNumber", device.getSerialNumber());
    }
    dev.put("deviceId", device.getDeviceId());
    return dev;
  }

  private void listDevices(Result result) {
    Map<String, UsbDevice> devices = m_Manager.getDeviceList();
    if (devices == null) {
      result.error("LIST_DEVICES_ERROR", "Could not get USB device list.", null);
      return;
    }
    List<HashMap<String, Object>> transferDevices = new ArrayList<>();

    for (UsbDevice device : devices.values()) {
      transferDevices.add(serializeDevice(device));
    }
    result.success(transferDevices);
  }

  private BroadcastReceiver createUsbStateChangeReceiver(final EventSink events) {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_USB_ATTACHED)) {
          Log.d(TAG, "ACTION_USB_ATTACHED");
          if (events != null) {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            HashMap<String, Object> msg = serializeDevice(device);
            msg.put("event", ACTION_USB_ATTACHED);
            events.success(msg);
          }
        } else if (intent.getAction().equals(ACTION_USB_DETACHED)) {
          Log.d(TAG, "ACTION_USB_DETACHED");
          if (events != null) {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            HashMap<String, Object> msg = serializeDevice(device);
            msg.put("event", ACTION_USB_DETACHED);
            events.success(msg);
          }
        }
      }
    };
  }

  private interface AcquirePermissionCallback {
    void onSuccess(UsbDevice device);

    void onFailed(UsbDevice device);
  }

  private interface OpenDeviceCallback {
    void onSuccess(UsbDevice device);

    void onFailed(UsbDevice device, String errorCode, String error);
  }

  private void acquirePermissions(UsbDevice device, AcquirePermissionCallback cb) {

    class BRC2 extends BroadcastReceiver {

      private UsbDevice m_Device;
      private AcquirePermissionCallback m_CB;

      BRC2(UsbDevice device, AcquirePermissionCallback cb) {
        m_Device = device;
        m_CB = cb;
      }

      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_USB_PERMISSION.equals(action)) {
          Log.e(TAG, "BroadcastReceiver intent arrived, entering sync...");
          applicationContext.unregisterReceiver(this);
          synchronized (this) {
            Log.e(TAG, "BroadcastReceiver in sync");
            /* UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE); */
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
              // createPort(m_DriverIndex, m_PortIndex, m_Result, false);
              m_CB.onSuccess(m_Device);
            } else {
              Log.d(TAG, "permission denied for device ");
              m_CB.onFailed(m_Device);
            }
          }
        }
      }
    }
    BRC2 usbReceiver = new BRC2(device, cb);
    PendingIntent permissionIntent = PendingIntent.getBroadcast(applicationContext, 0,
        new Intent(ACTION_USB_PERMISSION), 0);
    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    applicationContext.registerReceiver(usbReceiver, filter);
    m_Manager.requestPermission(device, permissionIntent);
  }

  private int setControlCommand(int requestType, int request, int value, int index, byte[] buffer, int length,
      int timeout) {
    int response = m_Connection.controlTransfer(requestType, request, value, index, buffer, length, 0);
    Log.i(TAG, "Control Transfer Response: " + String.valueOf(response));
    return response;
  }
}
