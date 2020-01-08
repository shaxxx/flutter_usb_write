import 'dart:async';

import 'package:async/async.dart';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_usb_write/flutter_usb_write.dart';
import 'package:mockito/mockito.dart';
import 'dart:convert';

class MockMethodChannel extends Mock implements MethodChannel {}

class MockEventChannel extends Mock implements EventChannel {}

void main() {
  MockMethodChannel methodChannel;
  MockEventChannel eventChannel;
  FlutterUsbWrite flutterUsbWrite;

  TestWidgetsFlutterBinding.ensureInitialized();
  UsbDevice device;

  setUp(() {
    device = UsbDevice(1046, 20497, "USB Portable Printer    ",
        "STMicroelectronics", 1002, "Printer");
    methodChannel = MockMethodChannel();
    eventChannel = MockEventChannel();
    flutterUsbWrite = FlutterUsbWrite.private(methodChannel, eventChannel);
  });

  group('Open device', () {
    test('open by deviceId', () async {
      Map<String, dynamic> args = {
        "vid": null,
        "pid": null,
        "deviceId": device.deviceId
      };
      when(methodChannel.invokeMethod('open', args))
          .thenAnswer((Invocation invoke) {
        return Future<Map<String, dynamic>>.value(device.toJson());
      });
      var result = await flutterUsbWrite.open(deviceId: device.deviceId);
      expect(result.toJson(), device.toJson());
    });

    test('open by vid:pid', () async {
      Map<String, dynamic> args = {
        "vid": device.vid,
        "pid": device.pid,
        "deviceId": null,
      };
      when(methodChannel.invokeMethod('open', args))
          .thenAnswer((Invocation invoke) {
        return Future<Map<String, dynamic>>.value(device.toJson());
      });
      var result = await flutterUsbWrite.open(
          vendorId: device.vid, productId: device.pid);
      expect(result.toJson(), device.toJson());
    });
  });

  group('Close device', () {
    test('close', () async {
      when(methodChannel.invokeMethod('close')).thenAnswer((Invocation invoke) {
        return Future<bool>.value(true);
      });
      var result = await flutterUsbWrite.close();
      expect(result, true);
    });
  });

  group('Write', () {
    test('write', () async {
      var bytes = ascii.encode("Hello world");
      Map<String, dynamic> args = {"bytes": bytes};
      when(methodChannel.invokeMethod('write', args))
          .thenAnswer((Invocation invoke) {
        return Future<bool>.value(true);
      });
      var result = await flutterUsbWrite.write(bytes);
      expect(result, true);
    });
  });

  group('controlTransfer', () {
    test('controlTransfer', () async {
      Map<String, dynamic> args = {
        "requestType": 161,
        "request": 1,
        "value": 0,
        "index": 0,
        "buffer": null,
        "length": 0,
        "timeout": 0,
      };
      when(methodChannel.invokeMethod<int>('controlTransfer', args))
          .thenAnswer((Invocation invoke) {
        return Future<int>.value(0);
      });
      var result =
          await flutterUsbWrite.controlTransfer(161, 1, 0, 0, null, 0, 0);
      expect(result, 0);
    });
  });

  group('device state', () {
    StreamController<Map<String, dynamic>> controller;

    setUp(() {
      controller = StreamController<Map<String, dynamic>>();
      when(eventChannel.receiveBroadcastStream())
          .thenAnswer((Invocation invoke) => controller.stream);
    });

    tearDown(() {
      controller.close();
    });

    test('calls receiveBroadcastStream once', () {
      flutterUsbWrite.usbEventStream;
      flutterUsbWrite.usbEventStream;
      flutterUsbWrite.usbEventStream;
      verify(eventChannel.receiveBroadcastStream()).called(1);
    });

    test('receive values', () async {
      final StreamQueue<UsbEvent> queue =
          StreamQueue<UsbEvent>(flutterUsbWrite.usbEventStream);

      Map<String, dynamic> msg1 = device.toJson();
      msg1["event"] = UsbEvent.ACTION_USB_ATTACHED;

      var event1 = UsbEvent();
      event1.device = device;
      event1.event = UsbEvent.ACTION_USB_ATTACHED;

      controller.add(msg1);

      var usbEvent1 = await queue.next;
      expect(usbEvent1.toJson(), event1.toJson());

      Map<String, dynamic> msg2 = device.toJson();
      msg2["event"] = UsbEvent.ACTION_USB_ATTACHED;

      var event2 = UsbEvent();
      event2.device = device;
      event2.event = UsbEvent.ACTION_USB_ATTACHED;

      controller.add(msg2);

      var usbEvent2 = await queue.next;
      expect(usbEvent2.toJson(), event2.toJson());
    });
  });

  tearDown(() {
    device = null;
    flutterUsbWrite = null;
    methodChannel = null;
    eventChannel = null;
  });
}
