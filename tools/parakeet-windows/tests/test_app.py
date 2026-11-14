"""Dependency-light tests: python -m unittest discover -s tests -v"""
import io
import unittest
from unittest.mock import patch

from app import app, extract_transcript


class TranscriptTests(unittest.TestCase):
    def test_segment_output(self):
        raw = 'Processing audio (16000 samples, 1.00 seconds)\nSegment 0: [0 -> 50] "Hola"\nSegment 1: [50 -> 100] "mundo"'
        self.assertEqual(extract_transcript(raw), "Hola mundo")

    def test_plain_output(self):
        self.assertEqual(extract_transcript("Processing audio (16000 samples)\nHola mundo\n"), "Hola mundo")

    def test_missing_file(self):
        with app.test_client() as client:
            response = client.post("/transcribe")
            self.assertEqual(response.status_code, 400)

    def test_bad_extension(self):
        with app.test_client() as client:
            response = client.post("/transcribe", data={"audio": (io.BytesIO(b"abc"), "a.exe")})
            self.assertEqual(response.status_code, 400)

    def test_valid_upload(self):
        with patch("app.transcribe", return_value="Hola mundo"):
            with app.test_client() as client:
                response = client.post("/transcribe", data={"audio": (io.BytesIO(b"fake"), "audio.wav")})
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json["text"], "Hola mundo")


if __name__ == "__main__":
    unittest.main()
