import re, pathlib
root = pathlib.Path("SpamShield/android/app/src/main")
m = root / "AndroidManifest.xml"
x = m.read_text()
x = x.replace("<application", '<uses-permission android:name="android.permission.CALL_PHONE" />\n    <application', 1)
svc = '''<service android:name=".SpamScreeningService"
        android:permission="android.permission.BIND_SCREENING_SERVICE"
        android:exported="true">
      <intent-filter><action android:name="android.telecom.CallScreeningService" /></intent-filter>
    </service>
  </application>'''
x = x.replace("</application>", svc, 1)
m.write_text(x)
app = next(root.rglob("MainApplication.kt"))
k = app.read_text()
assert "PackageList(this).packages.apply {" in k, "MainApplication format changed"
k = k.replace("PackageList(this).packages.apply {", "PackageList(this).packages.apply {\n          add(SpamPackage())", 1)
app.write_text(k)
print("patched")
