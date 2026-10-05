# Order Notifier

Personal motivation app for Android (Pixel). Posts random, local-only "new order"
notifications to *your own phone*. Nothing is sent anywhere; it only works on the
device it's installed on.

- Toggle on/off
- Random gap between notifications (min/max seconds) + random bursts (chance + max size)
- Customizable store name, currency, squircle icon (pick any image in-app), price range, items, order number
- Blank slate defaults: name "Store", grey squircle, no branding

## Install
GitHub -> Actions -> latest "Build APK" run -> download `app-debug` -> unzip -> install the APK
on your phone (allow "install unknown apps"). Or open the project in Android Studio and Run.

Change the default name in `Settings.kt` (`DEFAULT_STORE_NAME`).
