# Permanent signing setup

Android update ke liye har APK ko same private signing key se sign karna lazmi hai.

GitHub repository ke Settings > Secrets and variables > Actions mein yeh secrets banayein:

- THRAMART_KEYSTORE_BASE64
- THRAMART_STORE_PASSWORD
- THRAMART_KEY_ALIAS
- THRAMART_KEY_PASSWORD

Workflow file: `.github/workflows/build-signed-apk.yml`

Public repository mein `.jks` file commit na karein. Signing key ka secure backup zaroor rakhein.
