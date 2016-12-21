WVA Android Library
===================

The WVA Android Library (wvalib) is a Java library for interacting
with the web services of a Digi [Wireless Vehicle Bus Adapter][WVA]
(WVA).  This source has been contributed by
[Digi International][Digi].

  * Allows for reading vehicle data and diagnostic trouble codes over
    a vehicle's J1708 and CAN bus through polling or asynchronous
    subscription channels.

  * Assists in configuration of the WVA device.

[Digi]: http://www.digi.com
[WVA]: http://www.digi.com/products/wireless-vehicle-bus-adapter/wireless-vehicle-bus-adapter#overview


Support and Contributing
------------------------

Contributions to the project are very welcome. Please submit any issues you
find to the [GitHub issue tracker][issues]. If you have a change you would like
to have included in the library, please open a pull request against the
`develop` branch.

Library code on the `master` branch is of release quality and has been
code-reviewed and quality tested at the time of release. Any code added to the
repository between official releases can be found on the `develop` branch; code
on this branch has been code-reviewed but has not necessarily been fully
tested - use this at your own discretion.

[issues]: https://github.com/digidotcom/wvalib/issues

---

Requirements
------------

You will need:

  * A Wireless Vehicle Bus Adapter

  * The Android SDK and dependencies must be installed with API level
    20.

  * An Android environment (device or emulation) to develop your
    application and run tests.

Installation
------------

### Adding to an existing Gradle project

To import this library into an existing Android Gradle project, add the
following line to the `dependencies` section of your `build.gradle` file:

    compile 'com.digi.wva:wvalib:2.0+'


### Compiling the library yourself

It is recommended that you import the project into Android Stuio v2.2 or
above.

From the command line
Before you can build the library you must point to the location of the
Android SDK by setting your `ANDROID_HOME` environment variable or
creating a `local.properties` file and setting the `sdk.dir` property.

Usage
-----

The library is a standard [Android Gradle Library Plugin][plugin]
project with all of the typical Gradle tasks. (assemble, check, build,
clean, …).

Additional tasks added in `build.gradle`:

  - `generate<variant>Docs`: Process JavaDoc to HTML
  - `generate<variant>DocsJar`: Package documentation in a JAR
  - `generate<variant>SourceJar`: Package source in a JAR
  - `generate<variant>Jar`: Package class files in a JAR. This allows
    us to distribute a JAR rather than an AAR, as the library requires
    no Android resources.

[plugin]: http://tools.android.com/tech-docs/new-build-system/user-guide


High-Level Function Reference
-----------------------------

The API is documented through the use of JavaDoc documentation
comments. To get an HTML version of this documentation you can execute
the `generateReleaseDocs` Gradle task, which will place the HTML
documents under `wvalib/build/docs/javadocs`.

License
-------

This software is open-source software.  Copyright Digi International, 2014.

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this file,
You can obtain one at http://mozilla.org/MPL/2.0/.

