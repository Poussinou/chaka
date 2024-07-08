# Chaka
An Android Viewer
- Based on MuPDF Android Viewer
- Add support of page flipping in vertical direction.
- Add support of text direction of right to left. This is for the convenience of old chinese books.
- Add support of single column mode. It will split a double-page spread image from a scanned pdf book into two seperated pages. In the implemention, the first and last page will not be splitted.
- Improve navigation and visual experience.

Note: the package name "com.artifex.mupdf" has been changed to "net.timelegend.chaka".

# MuPDF Android Viewer

This project is a simplified variant of the full MuPDF Android app that only
supports viewing documents. The annotation editing and form filling features
are not present here.

This project builds both a viewer library and a viewer application.
The viewer library can be used to view PDF and other documents.

The application is a simple file chooser that shows a list of documents on the
external storage on your device, and hands off the selected file to the viewer
library.

## Prerequisites

You need a working Android development environment, consisting of the Android
SKD and the Android NDK. The easiest way is to use Android Studio to download
and install the SDK and NDK.

## Building

Download the project using Git:

	$ git clone git@github.com:elementdavv/chaka.git

Edit the local.properties file to point to your Android SDK directory:

	$ echo sdk.dir=$HOME/Android/Sdk > local.properties

If all tools have been installed as per the prerequisites, build the app using
the gradle wrapper:

	$ ./gradlew assembleRelease

If all has gone well, you can now find the app APKs in app/build/outputs/apk/,
with one APK for each ABI. There is also a universal APK which supports all
ABIs.

The library component can be found in lib/build/outputs/aar/lib-release.aar.

## Running

To run the app in the android emulator, first you'll need to set up an "Android
Virtual Device" for the emulator. Run "android avd" and create a new device.
You can also use Android Studio to set up a virtual device. Use the x86 ABI for
best emulator performance.

Then launch the emulator, or connect a device with USB debugging enabled:

	$ emulator -avd MyVirtualDevice &

Then copy some test files to the device:

	$ adb push file.pdf /mnt/sdcard/Download

Then install the app on the device:

	$ ./gradlew installDebug

To start the installed app on the device:

	$ adb shell am start -n net.timelegend.chaka.viewer.app/.LibraryActivity

To see the error and debugging message log:

	$ adb logcat

## Building the JNI library locally

The viewer library here is 100% pure java, but it uses the MuPDF fitz library,
which provides access to PDF rendering and other low level functionality.
The default is to use the JNI library artifact from the Ghostscript Maven
repository.

If you want to build the JNI code yourself, you will need to check out the
'jni' submodule recursively. You will also need a working host development
environment with a C compiler and GNU Make.

Either clone the original project with the --recursive flag, or initialize all
the submodules recursively by hand:

	chaka $ git submodule update --init
	chaka $ cd jni
	chaka/jni $ git submodule update --init
	chaka/jni $ cd libmupdf
	chaka/jni/libmupdf $ git submodule update --init

Then you need to run the 'make generate' step in the libmupdf directory:

	chaka/jni/libmupdf $ make generate

Once this is done, the build system should pick up the local JNI library
instead of using the Maven artifact.

## Release

To do a release you MUST first change the package name!

Do NOT use the net.timelegend domain for your custom app!

In order to sign a release build, you will need to create a key and a key
store.

	$ keytool -genkey -v -keystore app/android.keystore -alias MyKey \
		-validity 3650 -keysize 2048 -keyalg RSA

Then add the following entries to app/gradle.properties:

	release_storeFile=android.keystore
	release_storePassword=<your keystore password>
	release_keyAlias=MyKey
	release_keyPassword=<your key password>

If your keystore has been set up properly, you can now build a signed release.

## Maven

The library component of this project can be packaged as a Maven artifact.

The default is to create the Maven artifact in the 'MAVEN' directory. You can
copy thoes files to the distribution site manually, or you can change the
uploadArchives repository in build.gradle before running the uploadArchives
task.

	$ ./gradlew uploadArchives

Good Luck!
