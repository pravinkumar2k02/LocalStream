import sys
import unittest
from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import Qt
from api_client import StreamApiClient
from player_window import PlayerWindow

app = QApplication.instance()
if app is None:
    app = QApplication(sys.argv)
app.setQuitOnLastWindowClosed(False)

class TestStreamApiClient(unittest.TestCase):
    def setUp(self):
        self.client = StreamApiClient()

    def test_normalize_url(self):
        self.assertEqual(self.client.normalize_url("192.168.43.1:8080"), "http://192.168.43.1:8080")
        self.assertEqual(self.client.normalize_url("http://192.168.43.1:8080/"), "http://192.168.43.1:8080")
        self.assertEqual(self.client.normalize_url("https://10.0.0.1:9000//"), "https://10.0.0.1:9000")

    def test_build_stream_url(self):
        url = self.client.build_stream_url("http://192.168.43.1:8080", "12", "8492")
        self.assertEqual(url, "http://192.168.43.1:8080/stream/12?pin=8492")

    def test_format_size(self):
        self.assertEqual(self.client.format_size(10485760), "10.0 MB")
        self.assertEqual(self.client.format_size(3221225472), "3.0 GB")
        self.assertEqual(self.client.format_size(500), "500.0 B")


class TestPlayerWindow(unittest.TestCase):
    def setUp(self):
        self.file_info = {
            "id": "42",
            "name": "Sample_Movie_1080p.mkv",
            "mimeType": "video/x-matroska",
            "size": 3145728000,
            "size_formatted": "2.9 GB"
        }
        self.player_win = PlayerWindow(self.file_info, "")

    def test_window_initialization(self):
        self.assertIn("Sample_Movie_1080p.mkv", self.player_win.windowTitle())
        self.assertIsNotNone(self.player_win.video_widget)
        self.assertIsNotNone(self.player_win.controls_bar)
        self.assertIsNotNone(self.player_win.top_bar)
        self.assertEqual(self.player_win.volume_slider.value(), 85)

    def test_time_formatting(self):
        self.assertEqual(PlayerWindow._format_ms(0), "00:00:00")
        self.assertEqual(PlayerWindow._format_ms(65000), "00:01:05")
        self.assertEqual(PlayerWindow._format_ms(3665000), "01:01:05")

    def test_fullscreen_toggle(self):
        self.assertFalse(self.player_win.isFullScreen())
        self.player_win.toggle_fullscreen()
        self.assertEqual(self.player_win.fs_btn.text(), "🗗 Exit Fullscreen")
        self.assertTrue(self.player_win.idle_timer.isActive())

        # Toggle back
        self.player_win.toggle_fullscreen()
        self.assertEqual(self.player_win.fs_btn.text(), "⛶ Fullscreen")

    def test_relative_seeking(self):
        self.player_win.seek_relative(10)
        self.assertEqual(self.player_win.osd_badge.text(), "+10s ⏩")
        self.player_win.seek_relative(-10)
        self.assertEqual(self.player_win.osd_badge.text(), "⏪ 10s")

    def test_mute_toggle(self):
        self.assertFalse(self.player_win.audio_output.isMuted())
        self.player_win.toggle_mute()
        self.assertTrue(self.player_win.audio_output.isMuted())
        self.assertEqual(self.player_win.mute_btn.text(), "🔇")
        self.player_win.toggle_mute()
        self.assertFalse(self.player_win.audio_output.isMuted())
        self.assertEqual(self.player_win.mute_btn.text(), "🔊")


if __name__ == "__main__":
    runner = unittest.TextTestRunner(stream=sys.stdout, verbosity=2)
    suite = unittest.TestLoader().loadTestsFromNames([
        'test_client.TestStreamApiClient',
        'test_client.TestPlayerWindow'
    ])
    result = runner.run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
