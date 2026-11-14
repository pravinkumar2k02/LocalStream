import sys
import threading
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLineEdit, QPushButton, QTableWidget, QTableWidgetItem, QHeaderView,
    QLabel, QMessageBox, QCheckBox
)
from api_client import StreamApiClient, PinAuthError, ServerConnError
from player_window import PlayerWindow
from wake_manager import ScreenWakeManager


class FetchWorker(QThread):
    finished = pyqtSignal(list)
    error = pyqtSignal(str)

    def __init__(self, client: StreamApiClient, url: str, pin: str):
        super().__init__()
        self.client = client
        self.url = url
        self.pin = pin

    def run(self):
        try:
            files = self.client.fetch_files(self.url, self.pin)
            self.finished.emit(files)
        except Exception as e:
            self.error.emit(str(e))


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Local Stream Player - Client (Python)")
        self.resize(960, 640)
        self.setMinimumSize(780, 500)
        self.setStyleSheet("""
            QMainWindow { background-color: #121212; }
            QLabel { color: #E0E0E0; }
            QLineEdit { background-color: #262626; color: white; border: 1px solid #3A3A3A; border-radius: 4px; padding: 6px; }
            QPushButton { border-radius: 4px; padding: 6px 14px; font-weight: bold; }
            QTableWidget { background-color: #1B1B1B; color: #FFFFFF; gridline-color: #2D2D2D; border: 1px solid #2D2D2D; selection-background-color: #2979FF; }
            QHeaderView::section { background-color: #222222; color: #BBBBBB; padding: 6px; border: 1px solid #2D2D2D; font-weight: bold; }
        """)

        self.api_client = StreamApiClient()
        self.all_files = []
        self.active_players = []

        # Start ScreenWakeManager to keep laptop screen awake until application closes
        self.wake_manager = ScreenWakeManager(interval_seconds=30)
        self.wake_manager.start()

        self._build_ui()

    def _build_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        # Top Bar: Connection parameters
        conn_bar = QHBoxLayout()
        conn_bar.setSpacing(10)

        title = QLabel("📡 Local Stream")
        title.setStyleSheet("font-size: 16px; font-weight: bold; color: white;")

        url_lbl = QLabel("Server URL:")
        self.url_input = QLineEdit("http://192.168.43.1:8080")
        self.url_input.setFixedWidth(200)

        pin_lbl = QLabel("PIN Token:")
        self.pin_input = QLineEdit()
        self.pin_input.setPlaceholderText("4-digit PIN")
        self.pin_input.setFixedWidth(90)
        self.pin_input.setEchoMode(QLineEdit.EchoMode.Password)

        self.show_pin_cb = QCheckBox("Show")
        self.show_pin_cb.setStyleSheet("color: #AAAAAA; font-size: 11px;")
        self.show_pin_cb.toggled.connect(self._toggle_pin_visibility)

        self.connect_btn = QPushButton("⚡ Connect")
        self.connect_btn.setStyleSheet("background-color: #2979FF; color: white;")
        self.connect_btn.clicked.connect(self._connect_server)

        self.status_lbl = QLabel("Disconnected")
        self.status_lbl.setStyleSheet("color: #888888; font-size: 12px; margin-left: 6px;")

        conn_bar.addWidget(title)
        conn_bar.addWidget(url_lbl)
        conn_bar.addWidget(self.url_input)
        conn_bar.addWidget(pin_lbl)
        conn_bar.addWidget(self.pin_input)
        conn_bar.addWidget(self.show_pin_cb)
        conn_bar.addWidget(self.connect_btn)
        conn_bar.addWidget(self.status_lbl)
        conn_bar.addStretch(1)

        # Filter Bar + Anti-Sleep Status Badge
        filter_bar = QHBoxLayout()
        search_lbl = QLabel("🔍 Search:")
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Filter files by name or format...")
        self.search_input.textChanged.connect(self._filter_table)

        self.count_lbl = QLabel("0 files")
        self.count_lbl.setStyleSheet("color: #888888; font-size: 12px;")

        # Keep Screen Awake checkbox & status indicator
        self.wake_cb = QCheckBox("Keep Screen Awake")
        self.wake_cb.setChecked(True)
        self.wake_cb.setToolTip(
            "Simulates background user activity and uses Windows Power APIs to prevent "
            "the screen from timing out, dimming, or sleeping while this app runs."
        )
        self.wake_cb.setStyleSheet("color: #00E676; font-size: 11px; font-weight: bold; margin-left: 12px;")
        self.wake_cb.toggled.connect(self._toggle_screen_wake)

        filter_bar.addWidget(search_lbl)
        filter_bar.addWidget(self.search_input, stretch=1)
        filter_bar.addWidget(self.count_lbl)
        filter_bar.addWidget(self.wake_cb)

        # File List Table
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(["Type", "File Name", "MIME Type", "Size", "Action"])
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table.setColumnWidth(0, 80)
        self.table.setColumnWidth(2, 170)
        self.table.setColumnWidth(3, 110)
        self.table.setColumnWidth(4, 110)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.cellDoubleClicked.connect(self._on_row_double_clicked)

        # Bottom Hint Bar
        hint = QLabel("💡 Tip: Turn on Android Hotspot, connect your laptop to it, enter Server IP & PIN, and click Connect. Screen will stay awake until you close this app.")
        hint.setStyleSheet("color: #777777; font-size: 11px;")

        layout.addLayout(conn_bar)
        layout.addLayout(filter_bar)
        layout.addWidget(self.table, stretch=1)
        layout.addWidget(hint)

    def _toggle_pin_visibility(self, checked: bool):
        mode = QLineEdit.EchoMode.Normal if checked else QLineEdit.EchoMode.Password
        self.pin_input.setEchoMode(mode)

    def _toggle_screen_wake(self, checked: bool):
        if checked:
            self.wake_manager.start()
            self.wake_cb.setStyleSheet("color: #00E676; font-size: 11px; font-weight: bold; margin-left: 12px;")
            self.wake_cb.setText("Keep Screen Awake")
        else:
            self.wake_manager.stop()
            self.wake_cb.setStyleSheet("color: #888888; font-size: 11px; margin-left: 12px;")
            self.wake_cb.setText("Keep Screen Awake (Off)")

    def _connect_server(self):
        url = self.url_input.text().strip()
        pin = self.pin_input.text().strip()

        if not url:
            QMessageBox.warning(self, "Missing URL", "Please enter the Android Server URL.")
            return

        self.connect_btn.setEnabled(False)
        self.status_lbl.setText("Connecting...")
        self.status_lbl.setStyleSheet("color: #FFB300;")

        self.worker = FetchWorker(self.api_client, url, pin)
        self.worker.finished.connect(self._on_fetch_success)
        self.worker.error.connect(self._on_fetch_error)
        self.worker.start()

    def _on_fetch_success(self, files: list):
        self.connect_btn.setEnabled(True)
        self.all_files = files
        for f in self.all_files:
            f["size_formatted"] = StreamApiClient.format_size(f.get("size", 0))

        self.status_lbl.setText(f"Connected ({len(files)} files)")
        self.status_lbl.setStyleSheet("color: #00E676; font-weight: bold;")
        self._filter_table()

    def _on_fetch_error(self, err_msg: str):
        self.connect_btn.setEnabled(True)
        self.status_lbl.setText("Connection Failed")
        self.status_lbl.setStyleSheet("color: #FF5252;")
        QMessageBox.critical(self, "Connection Error", err_msg)

    def _filter_table(self):
        query = self.search_input.text().lower().strip()
        filtered = [
            f for f in self.all_files
            if not query or query in f.get("name", "").lower() or query in f.get("mimeType", "").lower()
        ]

        self.count_lbl.setText(f"{len(filtered)} of {len(self.all_files)} files")
        self.table.setRowCount(len(filtered))

        for row, f in enumerate(filtered):
            # Type icon
            mime = f.get("mimeType", "").lower()
            name = f.get("name", "").lower()
            if mime.startswith("video/") or any(name.endswith(ext) for ext in [".mkv", ".mp4", ".avi", ".webm", ".mov"]):
                type_str = "🎬 Video"
            elif mime.startswith("audio/") or any(name.endswith(ext) for ext in [".mp3", ".wav", ".flac", ".aac", ".ogg"]):
                type_str = "🎵 Audio"
            else:
                type_str = "📄 File"

            item_type = QTableWidgetItem(type_str)
            item_name = QTableWidgetItem(f.get("name", "Unknown"))
            item_mime = QTableWidgetItem(f.get("mimeType", ""))
            item_size = QTableWidgetItem(f.get("size_formatted", ""))

            self.table.setItem(row, 0, item_type)
            self.table.setItem(row, 1, item_name)
            self.table.setItem(row, 2, item_mime)
            self.table.setItem(row, 3, item_size)

            # Play Button
            btn = QPushButton("▶ Play")
            btn.setStyleSheet("background-color: #2979FF; color: white; padding: 4px 8px; font-size: 11px;")
            btn.clicked.connect(lambda _, file_data=f: self._open_player(file_data))
            self.table.setCellWidget(row, 4, btn)

            # Store file reference in item
            item_name.setData(Qt.ItemDataRole.UserRole, f)

    def _on_row_double_clicked(self, row: int, col: int):
        item = self.table.item(row, 1)
        if item:
            f = item.data(Qt.ItemDataRole.UserRole)
            if f:
                self._open_player(f)

    def _open_player(self, file_data: dict):
        base_url = self.url_input.text().strip()
        pin = self.pin_input.text().strip()
        stream_url = self.api_client.build_stream_url(base_url, file_data.get("id", ""), pin)

        player_win = PlayerWindow(file_data, stream_url)
        player_win.show()
        self.active_players.append(player_win)

    def closeEvent(self, event):
        # Cleanly stop the ScreenWakeManager and restore normal Windows power settings
        if hasattr(self, 'wake_manager') and self.wake_manager:
            self.wake_manager.stop()
        for p in self.active_players:
            try:
                p.close()
            except Exception:
                pass
        super().closeEvent(event)


def main():
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
