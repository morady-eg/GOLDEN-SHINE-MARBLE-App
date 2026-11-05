# GOLDEN SHINE MARBLE

تطبيق Android أصلي لإدارة عروض الأسعار وعقود المقاولات لشركة GOLDEN SHINE MARBLE.

## التقنية
- Kotlin وJetpack Compose
- Android Native
- واجهة عربية من اليمين إلى اليسار
- محرك تسعير يدعم المتر المربع والمقطوعية في المستند نفسه
- إعداد ضريبة القيمة المضافة لكل مستند
- اختبارات وحدات لمحرك التسعير
- GitHub Actions لاختبارات Gradle وبناء APK تجريبي

## البناء
يتطلب JDK 17 وAndroid SDK 35 وGradle 8.11.1.

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## الحالة
المرحلة الأولى: تأسيس المشروع والواجهة العربية ومحرك التسعير. يليها التخزين الدائم وإدارة المستندات وتوليد PDF.
