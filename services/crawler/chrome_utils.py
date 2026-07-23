"""
Helper dùng chung cho crawler Selenium (ITviec, TopCV).

undetected_chromedriver mặc định tự dò chromedriver phù hợp, nhưng trong
container này nó fetch nhầm driver mới hơn Chrome đã cài (image build 1 lần,
Chrome không tự update, còn Chrome-for-Testing channel mà uc tra cứu thì luôn
là bản mới nhất) -> "session not created: This version of ChromeDriver only
supports Chrome version X". Truyền version_main= đúng version Chrome đã cài
để uc patch driver khớp version, tránh phải rơi vào nhánh fallback.
"""

import logging
import re
import subprocess

logger = logging.getLogger(__name__)

CHROME_BINARIES = ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser")


def installed_chrome_major_version() -> int | None:
    for binary in CHROME_BINARIES:
        try:
            result = subprocess.run(
                [binary, "--version"],
                capture_output=True,
                text=True,
                timeout=10,
            )
        except (FileNotFoundError, OSError, subprocess.SubprocessError):
            continue
        match = re.search(r"(\d+)\.", result.stdout)
        if match:
            version = int(match.group(1))
            logger.info("Detected installed Chrome major version: %d (via %s)", version, binary)
            return version
    logger.warning("Could not detect installed Chrome version, falling back to uc auto-detect")
    return None
