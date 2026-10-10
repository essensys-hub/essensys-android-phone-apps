import unittest

import check_release_apk as c

OK_SIGNER = "Verifies\nVerified using v1 scheme (JAR signing): false\nVerified using v2 scheme (APK Signature Scheme v2): true\n"
OK_BADGING = "package: name='com.essensys.android'\nminSdkVersion:'26'\nnative-code: 'arm64-v8a' 'armeabi-v7a' 'x86' 'x86_64'\n"


class CheckTest(unittest.TestCase):
    def test_release_apk_is_v2_signed_with_arm64_NR_android_9(self):
        self.assertEqual(c.check(OK_SIGNER, OK_BADGING), [])

    def test_v1_only_signature_is_refused(self):
        signer = "Verifies\nVerified using v1 scheme (JAR signing): true\nVerified using v2 scheme (APK Signature Scheme v2): false\n"
        self.assertTrue(any("v2" in p for p in c.check(signer, OK_BADGING)))

    def test_missing_arm64_is_refused(self):
        badging = OK_BADGING.replace("'arm64-v8a' ", "")
        self.assertTrue(any("arm64" in p for p in c.check(OK_SIGNER, badging)))

    def test_unsigned_is_refused(self):
        self.assertTrue(c.check("DOES NOT VERIFY\n", OK_BADGING))

    def test_junit_failure_is_reported(self):
        xml = c.junit(c.TEST_NAME, ["signature v2 absente"])
        self.assertIn('failures="1"', xml)
        self.assertIn(c.TEST_NAME, xml)


if __name__ == "__main__":
    unittest.main()
