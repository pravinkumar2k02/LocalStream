import io
import socket
import secrets
import qrcode
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QImage, QPixmap
from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QLineEdit, QFrame, QMessageBox, QApplication
)


class PairingQrDialog(QDialog):
    """
    Dialog displaying dynamic high-entropy cryptographic session token
    as a QR code for zero-friction pairing with the Android LocalStream app.
    """

    def __init__(self, parent=None, initial_token=None):
        super().__init__(parent)
        self.setWindowTitle("📱 Pair Laptop via Secure QR Code")
        self.setFixedSize(480, 580)
        self.setStyleSheet("""
            QDialog {
                background-color: #181818;
                color: #FFFFFF;
            }
            QLabel {
                color: #DDDDDD;
            }
            QLineEdit {
                background-color: #242424;
                color: #00E676;
                border: 1px solid #3A3A3A;
                border-radius: 4px;
                padding: 6px;
                font-family: monospace;
                font-size: 11px;
            }
            QPushButton {
                border-radius: 4px;
                padding: 7px 14px;
                font-weight: bold;
                font-size: 12px;
            }
        """)

        try:
            self.hostname = socket.gethostname()
        except Exception:
            self.hostname = "Laptop"

        self.current_token = initial_token if initial_token else self._generate_token()
        self._build_ui()
        self._refresh_qr()

    def _generate_token(self) -> str:
        # Generates a 256-bit cryptographically secure hexadecimal token (64 chars)
        return secrets.token_hex(32)

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(14)

        # Title & Subtitle
        title = QLabel("🔐 End-to-End Encrypted Session Pairing")
        title.setStyleSheet("font-size: 15px; font-weight: bold; color: #FFFFFF;")
        layout.addWidget(title)

        desc = QLabel(
            "Scan this QR code with the LocalStream Android app to securely authorize "
            "this laptop session with a dynamic 256-bit cryptographic key."
        )
        desc.setWordWrap(True)
        desc.setStyleSheet("color: #AAAAAA; font-size: 12px;")
        layout.addWidget(desc)

        # QR Code Display Container
        qr_container = QFrame()
        qr_container.setStyleSheet("background-color: #FFFFFF; border-radius: 8px; padding: 10px;")
        qr_layout = QVBoxLayout(qr_container)
        qr_layout.setContentsMargins(8, 8, 8, 8)
        qr_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.qr_label = QLabel()
        self.qr_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.qr_label.setFixedSize(280, 280)
        qr_layout.addWidget(self.qr_label)
        layout.addWidget(qr_container, alignment=Qt.AlignmentFlag.AlignCenter)

        # Token Display & Copy Row
        token_label = QLabel("Generated Session Token:")
        token_label.setStyleSheet("font-size: 11px; color: #888888;")
        layout.addWidget(token_label)

        token_row = QHBoxLayout()
        token_row.setSpacing(8)

        self.token_input = QLineEdit(self.current_token)
        self.token_input.setReadOnly(True)

        copy_btn = QPushButton("📋 Copy")
        copy_btn.setStyleSheet("background-color: #2D2D2D; color: #DDDDDD; font-size: 11px; padding: 6px 10px;")
        copy_btn.clicked.connect(self._copy_token)

        regen_btn = QPushButton("🔄 New Key")
        regen_btn.setToolTip("Generate a fresh 256-bit key for this session")
        regen_btn.setStyleSheet("background-color: #2D2D2D; color: #DDDDDD; font-size: 11px; padding: 6px 10px;")
        regen_btn.clicked.connect(self._regenerate_token)

        token_row.addWidget(self.token_input, stretch=1)
        token_row.addWidget(copy_btn)
        token_row.addWidget(regen_btn)
        layout.addLayout(token_row)

        # Action Buttons
        btn_row = QHBoxLayout()
        btn_row.setSpacing(10)

        cancel_btn = QPushButton("Close")
        cancel_btn.setStyleSheet("background-color: #2D2D2D; color: #AAAAAA;")
        cancel_btn.clicked.connect(self.reject)

        self.apply_btn = QPushButton("✅ Use This Token & Connect")
        self.apply_btn.setStyleSheet("background-color: #2979FF; color: #FFFFFF;")
        self.apply_btn.clicked.connect(self.accept)

        btn_row.addWidget(cancel_btn)
        btn_row.addWidget(self.apply_btn)
        layout.addLayout(btn_row)

    def _refresh_qr(self):
        qr_data = f"localstream://auth?token={self.current_token}&client={self.hostname}"
        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=6,
            border=2,
        )
        qr.add_data(qr_data)
        qr.make(fit=True)

        img = qr.make_image(fill_color="#000000", back_color="#FFFFFF")
        buf = io.BytesIO()
        img.save(buf, format="PNG")

        qimg = QImage.fromData(buf.getvalue())
        pixmap = QPixmap.fromImage(qimg)
        self.qr_label.setPixmap(pixmap.scaled(280, 280, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
        self.token_input.setText(self.current_token)

    def _regenerate_token(self):
        self.current_token = self._generate_token()
        self._refresh_qr()

    def _copy_token(self):
        clipboard = QApplication.clipboard()
        clipboard.setText(self.current_token)
        QMessageBox.information(self, "Token Copied", "Session token copied to clipboard!")

    def get_token(self) -> str:
        return self.current_token
