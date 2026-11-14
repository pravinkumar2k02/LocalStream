import urllib.parse
import urllib3
import requests

# Suppress self-signed certificate warnings for local hotspot streaming
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class PinAuthError(Exception):
    pass


class RateLimitError(Exception):
    pass


class IpBannedError(Exception):
    pass


class ServerConnError(Exception):
    pass


class StreamApiClient:
    def __init__(self):
        self.session = requests.Session()
        # Disable SSL verification for self-signed certificates on local network
        self.session.verify = False

    @staticmethod
    def normalize_url(server_url: str) -> str:
        if not server_url or not server_url.strip():
            return "https://192.168.43.1:8443"
        url = server_url.strip()
        if not url.startswith("http://") and not url.startswith("https://"):
            if ":8443" in url:
                url = "https://" + url
            else:
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

    def build_stream_url(self, base_url: str, file_id: str, token: str) -> str:
        base = self.normalize_url(base_url)
        encoded_token = urllib.parse.quote(token.strip() if token else "")
        return f"{base}/stream/{file_id}?token={encoded_token}"

    def fetch_files(self, server_url: str, token: str) -> list[dict]:
        base = self.normalize_url(server_url)
        encoded_token = urllib.parse.quote(token.strip() if token else "")
        endpoint = f"{base}/files?token={encoded_token}"
        headers = {
            "Accept": "application/json",
            "X-Auth-Token": token.strip() if token else "",
            "X-PIN": token.strip() if token else ""
        }

        try:
            resp = self.session.get(endpoint, headers=headers, timeout=8)
            if resp.status_code == 401:
                raise PinAuthError(
                    "Unauthorized: Session token or PIN was not recognized.\n"
                    "Tap 'Scan Laptop QR' in the Android app to authorize this laptop instantly!"
                )
            if resp.status_code == 403:
                raise IpBannedError(
                    "Access Blocked: Your IP address is temporarily banned due to excessive failed attempts.\n"
                    "Please wait for the ban duration to expire or unban in the Android app."
                )
            if resp.status_code == 429:
                raise RateLimitError("Rate limit exceeded! Please wait a few seconds before trying again.")
            if resp.status_code != 200:
                raise ServerConnError(f"Server returned HTTP {resp.status_code}: {resp.text}")

            data = resp.json()
            if not isinstance(data, list):
                return []
            return data
        except (PinAuthError, RateLimitError, IpBannedError):
            raise
        except requests.exceptions.RequestException as e:
            raise ServerConnError(
                f"Failed to connect to server at {base}.\n"
                f"Ensure your laptop is connected to the phone's Hotspot or Wi-Fi, "
                f"and that 'Start Server' is active.\nError: {e}"
            )
        except Exception as e:
            raise ServerConnError(f"Error fetching files: {e}")
