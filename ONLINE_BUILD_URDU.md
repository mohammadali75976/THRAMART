# Android Studio ke baghair online APK build

1. Is ZIP ko extract karein.
2. GitHub par private repository banayein.
3. Extracted folder ke andar ki tamam files/folders upload karein.
4. Hidden `.github` folder upload na ho to GitHub par manually yeh file banayein:

```text
.github/workflows/build-apk.yml
```

5. Root par mojood `BUILD_WORKFLOW_COPY.txt` ka code us file mein paste karke Commit karein.
6. `Actions -> Build Thramart Kiosk APK` kholen.
7. Latest green run ke neeche `Artifacts -> Thramart-Kiosk-debug` download karein.
8. Artifact ZIP extract karke `app-debug.apk` milegi.

## Pehla test

APK install karke app khol sakte hain, lekin full kiosk tab tak nahi lagega jab tak app Device Owner na ho.

## Default admin PIN

```text
2552
```

## URL

App ke `ADMIN / EXIT KIOSK` option mein PIN dal kar Company URL paste karein.
