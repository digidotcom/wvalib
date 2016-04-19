# 2.1.0.rc2

**Improvements/new features**

  * Update build scripts to be compatible with Android Studio 2.0 and the latest Android SDK build
    tools.

# 2.1.0.rc1

**Improvements/new features**

  * Added several new methods on the `WVA` class:
    * `setHttpLoggingEnabled`, `getHttpLoggingEnabled`: Control whether the HTTP client will log
      request/response information. Previously this logging could not be disabled.
      * Logging is now off by default.
    * `uriGet`, `uriPut`, `uriDelete`: Perform HTTP requests against arbitrary web services URIs.
      Previously the APIs exposed by this library only allowed access to particular web services trees.
    * `subscribeToUri`, `unsubscribeFromUri`: Add/remove subscriptions to arbitrary web services
      URIs. Previously this library was capable of subscribing only to vehicle and fault code data
      (`vehicle/data/` and `vehicle/dtc`, respectively).
    * `createUriAlarm`, `deleteUriAlarm`: Add/remove alarms for arbitrary web services URIs.
      Previously this library was capable of adding alarms for vehicle and fault code data.
      * NOTE: The WVA firmware imposes limits on which web services URIs can have alarms. See the
        WVA documentation for more information.
    * Various other methods related to URI data/subscriptions (`addUriListener`, etc.)
  * Added a new constructor to the `WVA` class: `WVA(Inet4Address address)`. This allows for simpler
    interoperability with existing code which uses the `Inet4Address` class, such as Digi's
    [ADDP Java library](https://bintray.com/digidotcom/maven/addplib/view).
  * Added the `getRawValue()` method to the `AbstractVehicleResponse` class, providing access to the
    "raw" value taken from the parsed JSON event.
    * You can use this method to access the value received from subscription events for
      `vehicle/ignition` or the `IgnitionSwitchStatus` data endpoint, for example. In these cases,
      `.getValue()` will return null.
  * Updated the library's build configuration for compatibility with Android Studio 1.2 and above.

**Known issues**

  * The library cannot properly parse subscription/alarm data for certain web
    services URIs, such as `hw/buttons/reset`.
    * This is because we currently assume that the event value's key matches
      the last element of the URI. This is not always true.
    * This behavior will be fixed in an update soon.
