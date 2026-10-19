# Blackb0x Android host

USB host client for an Apple TV 2,1 (A1378) in DFU or recovery. USB-C OTG on the phone to the Apple TV micro-USB port. Power cable stays in the Apple TV.

Matches `05ac:1227` (DFU) and `05ac:1281` (recovery).

DFU upload is control `0x21` request `1`, 2048-byte blocks, GETSTATUS `0xA1` request `3`, then a zero-length DNLOAD.

Recovery commands are vendor control `0x40/0`. The reply is `0xC0/0`.

The pwned iBSS and ramdisk are the files `bake-firmware --device AppleTV2,1` already writes. Pick that file in the app. The phone does not fetch an IPSW. A4 uses SHAtter. checkm8 does not apply to A1378.

tvOS 7.1.2 is tethered. tvOS 6.1.4 untethered still needs the first ramdisk boot from the bake.

Hold Menu + Down about six seconds until the LED strobes.

```
./gradlew :app:assembleDebug
```
