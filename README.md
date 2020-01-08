# flutter_usb_write

Write raw data to USB device on Android.

Unlike [USB Serial]() plugin, this plugin DOES NOT use serial protocol.

For example this can be use to print on USB POS printer by sending ESC\POS bytes.

## Supported Android devices

Only devices with USB Host support (USB OTG) can communicate with USB devices.

## Supported USB devices

Potentially you can write to any USB device (see [Limitations](#limitations)).

Plugin has been tested with multiple USB POS printers.

## Features

- list connected USB devices
- creates stream to listen on USB attached/detached events
- obtain permissions automatically (see [Permissions](#permissions))
- open connection to selected USB device
- send bytes to USB devices using open connection
- close open connection
- send controlTransfer bytes 

## Limitations

- doesn't suppport receiving messages over USB connection (IE. out of paper message)
- connection is fixed to first interface on selected USB device.
- only supports [USB_ENDPOINT_XFER_BULK](https://developer.android.com/reference/android/hardware/usb/UsbConstants.html#USB_ENDPOINT_XFER_BULK) endpoints
- only supports USB endpoints where direction is NOT [USB_DIR_IN]( https://developer.android.com/reference/android/hardware/usb/UsbConstants.html#USB_DIR_IN)

## Permissions

If you don't mind asking for permission everytime USB device is physically connected to Android device there's nothing to do. Plugin will ask for permissions when needed. 
However if you don't want to ask for permission everytime USB device is physically connected add 

```xml
	<intent-filter>
		<action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
	</intent-filter>

	<meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
		android:resource="@xml/device_filter" />
```
to your AndroidManifest.xml

and place device_filter.xml 

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 0x1CBE / 0x0003: USB POS printer 58 mm -->
    <usb-device vendor-id="7358" product-id="3" />
    <!-- 0x1CB0 / 0x0003: USB POS printer 58 mm -->
    <usb-device vendor-id="7344" product-id="3" />
    <!-- 0x0483 / 0x5740: USB POS printer 58 mm -->
    <usb-device vendor-id="1155" product-id="22336" />
    <!-- 0x0493 / 0x8760: USB POS printer 58 mm -->
    <usb-device vendor-id="1171" product-id="34656" />
    <!-- 0x0416 / 0x5011: USB POS printer 58 mm (Issyzone Pos IMP006B) -->
    <usb-device vendor-id="1046" product-id="20497" />
    <!-- 0x0416 / 0xAABB: USB POS printer 58 mm -->
    <usb-device vendor-id="1046" product-id="43707" />
    <!-- 0x1659 / 0x8965: USB POS printer 58 mm -->
    <usb-device vendor-id="5721" product-id="35173" />
    <!-- 0x0483 / 0x5741: USB POS printer 58 mm -->
    <usb-device vendor-id="1155" product-id="22337" />
    <!-- 0x4B43 / 0x3830: USB POS printer 80 mm (GoojPrt MTP-3) -->
    <usb-device vendor-id="19267" product-id="14384" />
    <!-- 0x0525 / 0xA700: USB POS printer 80 mm (MicroPOS WTP100II) -->
    <usb-device vendor-id="1317" product-id="42752" />
    <!-- 0x0525 / 0xA702: USB POS printer 58 mm (Sewoo LK-P20) -->
    <usb-device vendor-id="1317" product-id="42754" />
    <!-- 0x0416 / 0x5011: USB POS printer 58 mm -->
    <usb-device vendor-id="1046" product-id="20497" />      
</resources>
```
in the res/xml directory (modify accordingly to your devices).
This will automatically open your application when USB device is physically connected.
Once permission is allowed it won't ask anymore. 

## Getting Started

First, you'll probably want to list all USB devices connected to you Android device.

```dart
try {
      List<UsbDevice> devices = await _flutterUsbPos.listDevices();
    } on PlatformException catch (e) {
      print(e.message);
    }
```

Once you found the device, you'll need to open connection before attempting to write.
Each time you open connection device will get new deviceId. 
To open connection always use vid:pid parameters, method will return new ```UsbDevice``` object with ```deviceId``` value.
```dart
  Future<UsbDevice> _connect(UsbDevice device) async {
    try {
      var result = await _flutterUsbPos.open(
        vendorId: device.vid,
        productId: device.pid,
      );
      return result;
    } on PermissionException {
      print("Not allowed to do that");
      return null;
    } on PlatformException catch (e) {
      print(e.message);
      return null;
    }
  }
```

After that you can try to write to device.
Function returns true if number of bytes written is >= 0;
```dart
bool result = await _flutterUsbPos.write(Uint8List.fromList("Hello world".codeUnits));
```

Once you're done writing, close connection to release resources.
```dart
   Future _disconnect() async {
    try {
      await _flutterUsbPos.close();
    } on PlatformException catch (e) {
      print(e.message);
    }
  }
```

For more info see tests and example project.
