# Referenced by app/build.gradle. Release minification is still OFF (minifyEnabled false);
# these rules are ready for when the team turns it on. Test a minified build on a device
# before shipping: Firestore maps documents to the model classes by reflection.
-keep class com.campustrade.auth.model.** { *; }
-keepattributes Signature, *Annotation*
