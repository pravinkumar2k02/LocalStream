import sys
import time
import ctypes
import threading
from typing import Optional


class ScreenWakeManager:
    """
    Prevents the laptop screen from turning off, locking, or sleeping
    while the application is running by combining:
    1. Windows SetThreadExecutionState API (official display keep-awake).
    2. Periodic background user input simulation (harmless F15 virtual key
       and microscopic mouse nudge heartbeat) to defeat corporate timeout
       policies and screensavers.
    """

    ES_CONTINUOUS = 0x80000000
    ES_SYSTEM_REQUIRED = 0x00000001
    ES_DISPLAY_REQUIRED = 0x00000002

    VK_F15 = 0x7E  # Harmless standard unmapped virtual key (produces no character or shortcut)

    def __init__(self, interval_seconds: int = 30):
        self.interval = interval_seconds
        self.is_running = False
        self._thread: Optional[threading.Thread] = None
        self._is_windows = sys.platform.startswith("win")
        if self._is_windows:
            self.kernel32 = ctypes.windll.kernel32
            self.user32 = ctypes.windll.user32
        else:
            self.kernel32 = None
            self.user32 = None

    def start(self):
        """Starts keeping the display and system awake."""
        if not self._is_windows or self.is_running:
            return
        self.is_running = True

        # 1. Official Windows kernel power manager request
        try:
            self.kernel32.SetThreadExecutionState(
                self.ES_CONTINUOUS | self.ES_SYSTEM_REQUIRED | self.ES_DISPLAY_REQUIRED
            )
        except Exception:
            pass

        # 2. Launch background user input simulation thread
        self._thread = threading.Thread(
            target=self._heartbeat_loop,
            daemon=True,
            name="ScreenWakeHeartbeatThread"
        )
        self._thread.start()

    def _heartbeat_loop(self):
        """Periodically simulates user input to keep display alive."""
        while self.is_running:
            try:
                # Refresh native display keep-alive state
                self.kernel32.SetThreadExecutionState(
                    self.ES_DISPLAY_REQUIRED | self.ES_SYSTEM_REQUIRED
                )

                # Simulate harmless F15 key down & key up
                self.user32.keybd_event(self.VK_F15, 0, 0, 0)
                self.user32.keybd_event(self.VK_F15, 0, 2, 0)

                # Microscopic mouse nudge (1 px right then left) to reset OS idle timer
                self.user32.mouse_event(0x0001, 1, 0, 0, 0)
                self.user32.mouse_event(0x0001, -1, 0, 0, 0)
            except Exception:
                pass

            # Sleep in 0.5s increments so stop() responds immediately
            for _ in range(max(1, int(self.interval * 2))):
                if not self.is_running:
                    break
                time.sleep(0.5)

    def stop(self):
        """Restores standard Windows power management."""
        if not self.is_running:
            return
        self.is_running = False

        if self._is_windows:
            try:
                self.kernel32.SetThreadExecutionState(self.ES_CONTINUOUS)
            except Exception:
                pass
