#import "FlutterUsbWritePlugin.h"
#if __has_include(<flutter_usb_write/flutter_usb_write-Swift.h>)
#import <flutter_usb_write/flutter_usb_write-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_usb_write-Swift.h"
#endif

@implementation FlutterUsbWritePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterUsbWritePlugin registerWithRegistrar:registrar];
}
@end
