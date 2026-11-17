# Virshdroid

Manage libvirt virtual machines from your Android phone. Virshdroid connects to your servers over SSH and runs `virsh` for you: no agent, daemon or web panel to install on the host.

## Features

- List every virtual machine on a host with its current state (`virsh list --all`).
- Start, shut down (graceful ACPI) or destroy (force power-off) a virtual machine.
- Save several servers and switch between them from the home screen menu.
- Sign in with a password or a private key (OpenSSH or PEM, with an optional passphrase). Keys can be pasted or imported from a file.
- Material 3 Expressive UI with dynamic color and dark theme.

## Security

- **Host key pinning.** On first connection the app shows the server's SHA-256 host key fingerprint and connects only after you trust it. If the key later changes, the app refuses to connect and warns you. Changing a server's host or port requires trusting it again.
- **Encrypted storage.** Saved servers, including passwords and private keys, are encrypted with AES-GCM using a key held in the Android Keystore. The key never leaves the device.
- **No backups.** Cloud backup and device-to-device transfer are disabled, so credentials never leave the phone.
- **No tracking.** The app has no analytics, ads or network access other than the SSH connections you configure.

## Requirements

**Phone:** Android 12 (API 31) or newer. On Android 17 and newer the app asks for the local network permission so it can reach servers on your LAN.

**Server:**

- An SSH server that accepts password or public key authentication.
- `virsh` on the `PATH` of the SSH user.
- Access to the system libvirt instance (`qemu:///system`). Usually this means the user is in the `libvirt` group:

  ```sh
  sudo usermod -aG libvirt "$USER"
  ```

Virshdroid always connects to `qemu:///system`; session (`qemu:///session`) and other hypervisor URIs are not supported.

## Install

Download the APK from the [releases page](https://github.com/haashemi/virshdroid/releases) and open it on your phone, or build it yourself.

## Build

You need JDK 21 and the Android SDK (platform 37). Point Gradle at the SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=/path/to/sdk`.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

### Signed release builds

Release builds are signed only when a `keystore.properties` file exists in the project root. Both it and the keystore are ignored by git.

1. Create a keystore:

   ```sh
   keytool -genkeypair -v -keystore release.jks -alias virshdroid -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties`:

   ```properties
   storeFile=release.jks
   storePassword=<store password>
   keyAlias=virshdroid
   keyPassword=<key password>
   ```

3. Build:

   ```sh
   ./gradlew assembleRelease
   ```

The signed APK is written to `app/build/outputs/apk/release/`. Without `keystore.properties` the release APK is built unsigned.

## License

Virshdroid is released into the public domain under [The Unlicense](LICENSE).
