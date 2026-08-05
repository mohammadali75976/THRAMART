# GitHub par pehli signed APK

1. Is source ZIP ko extract karke nayi GitHub repository mein tamam files upload karein. `.github` folder bhi upload hona chahiye.
2. GitHub mein **Actions** tab kholein.
3. Workflow **1 - First Signed Build - Save Key** select karein.
4. **Run workflow** dabayein.
5. Build complete hone par artifact **Thramart-v4-FIRST-BUILD-SAVE-ALL-FILES** download karein.
6. Artifact ke andar signed APK ke saath JKS aur signing details hongi. Tamam files ka private backup banayein.
7. Production phone par pehle `Thramart-WA-Guard-v4-signed.apk`, phir `Thramart-Kiosk-v4-signed.apk` install karein.
8. JKS/password/Base64 file ko public GitHub mein upload na karein.

Future update ke liye artifact ki Base64 aur password values ko GitHub repository secrets mein save karke workflow **2 - Future Signed Update** run karein.
