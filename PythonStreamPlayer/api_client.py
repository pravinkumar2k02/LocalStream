import urllib.parse
import requests

class PinAuthError(Exception):
    pass

class ServerConnError(Exception):
    pass

class StreamApiClient:
    def __init__(self):
        self.session = requests.Session()

    @staticmethod
    def normalize_url(server_url: str) -> str:
        if not server_url or not server_url.strip():
            return "http://192.168.43.1:8080"
        url = server_url.strip()
        if not url.startswith("http://") and not url.startswith("https://"):
            url = "http://" + url
        return url.rstrip("/")

    @staticmethod
    def format_size(size_bytes: int) -> str:
        if size_bytes <= 0:
            return "0 B"
        units = ["B", "KB", "MB", "GB", "TB"]
        import math
        digit_groups = min(len(units) - 1, int(math.log10(size_bytes) / math.log10(1024)))
        val = size_bytes / (1024 ** digit_groups)
        return f"{val:.1f} {units[digit_groups]}"

    def build_stream_url(self, base_url: str, file_id: str, pin: str) -> str:
        base = self.normalize_url(base_url)
        encoded_pin = urllib.parse.quote(pin.strip() if pin else "")
        return f"{base}/stream/{file_id}?pin={encoded_pin}"

    def fetch_files(self, server_url: str, pin: str) -> list[dict]:
        base = self.normalize_url(server_url)
        encoded_pin = urllib.parse.quote(pin.strip() if pin else "")
        endpoint = f"{base}/files?pin={encoded_pin}"
        headers = {
            "Accept": "application/json",
            "X-PIN": pin.strip() if pin else ""
        }

        try:
            resp = self.session.get(endpoint, headers=headers, timeout=8)
            if resp.status_code == 401:
                raise PinAuthError("Invalid or missing PIN token! Please verify the PIN shown in the Android app.")
            if resp.status_code != 200:
                raise ServerConnError(f"Server returned HTTP {resp.status_code}: {resp.text}")

            data = resp.json()
            if not isinstance(data, list):
                return []
            return data
        except PinAuthError:
            raise
        except requests.exceptions.RequestException as e:
            raise ServerConnError(
                f"Failed to connect to server at {base}.\n"
                f"Ensure your laptop is connected to the Android hotspot or same Wi-Fi, "
                f"and that 'Start Server' is active in the Android app.\nError: {e}"
            )
        except Exception as e:
            raise ServerConnError(f"Error fetching files: {e}")
