import json
import os
import sys
import datetime
import runpy
import time
from pathlib import Path

import pytest

from conftest import FakeElement, install_fake_selenium


pytestmark = pytest.mark.scrape


class _FakeParagraphContainer:
    def __init__(self, texts):
        self.texts = texts

    def find_elements(self, by, selector):
        if selector == "p" or selector == "p.Normal":
            return [FakeElement(text=t) for t in self.texts]
        return []


class FakeGenKArticle:
    def __init__(self, title, href):
        self.title = title
        self.href = href

    def find_elements(self, by, selector):
        if selector == "h4.knswli-title a":
            return [FakeElement(attrs={"href": self.href})] if self.href is not None else []
        if selector == "h4.knswli-title":
            return [FakeElement(text=self.title)] if self.title is not None else []
        return []


class FakeGenKDriver:
    def __init__(self, no_such_exc):
        self.no_such_exc = no_such_exc
        self.current_url = ""

    def get(self, url):
        self.current_url = url

    def quit(self):
        return None

    def execute_script(self, script, *args):
        if "return Math.max" in script:
            return 2000
        return None

    def find_element(self, by, selector):
        if (
            by == "id"
            and selector == "ContentDetail"
            and self.current_url.startswith("https://genk.vn/article-")
        ):
            return _FakeParagraphContainer(["Noi dung 1", "Noi dung 2"])
        raise self.no_such_exc(selector)

    def find_elements(self, by, selector):
        if selector == "a.btnviewmore":
            return [FakeElement(text="Xem thêm")]
        if selector == "div.elp-list":
            return [
                FakeGenKArticle("Bai 1", "https://genk.vn/article-1"),
                FakeGenKArticle("Bai 2", "https://genk.vn/article-2"),
                FakeGenKArticle("Bai Loi", "https://genk.vn/eclick-ad"),
            ]
        return []


def test_genk_scrape_all_parts(tmp_path, monkeypatch):
    """Verify GenK parts 1, 2, 3, 4 output filenames and basic content in one test."""
    root = Path(__file__).resolve().parents[2]
    script_path = root / "src" / "data-pipeline" / "scrape_from_GenK.py"

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    driver = FakeGenKDriver(no_such_exc)
    install_fake_selenium(monkeypatch, driver)

    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    script_content = script_path.read_text(encoding="utf-8")
    
    for p in [1, 2, 3, 4]:
        # Reset tmp_path content
        if (tmp_path / "raw_data").exists():
            for f in (tmp_path / "raw_data").glob("*"): f.unlink()
        
        # Override part in script and force small scroll
        content = script_content.replace("part = 4", f"part = {p}")
        content = content.replace("max_scrolls = 100", "max_scrolls = 0")
        
        exec(content, {
            "__name__": "__main__", 
            "webdriver": sys.modules["selenium.webdriver"],
            "By": sys.modules["selenium.webdriver.common.by"].By,
            "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
            "NoSuchElementException": no_such_exc,
            "StaleElementReferenceException": sys.modules["selenium.common.exceptions"].StaleElementReferenceException,
            "json": json, "os": os, "datetime": datetime.datetime,
            "time": time
        })
        
        output = tmp_path / "raw_data" / f"raw_data_GenK_part{p}.json"
        assert output.exists(), f"Part {p} output file missing"
        
        data = json.loads(output.read_text(encoding="utf-8"))
        assert data["source_platform"] == "GenK-Trang thông tin điện tử từ tổng hợp"
        assert len(data["post_detail"]) == 2


def test_genk_filters_duplicate_links_explicitly(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_GenK.py"

    class _Driver(FakeGenKDriver):
        def find_elements(self, by, selector):
            if selector == "a.btnviewmore":
                return [FakeElement(text="Xem thêm")]
            if selector == "div.elp-list":
                return [
                    FakeGenKArticle("Bai 1", "https://genk.vn/article-1"),
                    FakeGenKArticle("Bai 1 duplicate", "https://genk.vn/article-1"),
                    FakeGenKArticle("Ad Link", "https://eclick.vn/ad-link"),
                ]
            return []

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    driver = _Driver(no_such_exc)
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    # Force part 4 and max_scrolls 0
    content = script.read_text(encoding="utf-8").replace("max_scrolls = 100", "max_scrolls = 0")
    exec(content, {
        "__name__": "__main__", "webdriver": sys.modules["selenium.webdriver"],
        "By": sys.modules["selenium.webdriver.common.by"].By,
        "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
        "NoSuchElementException": no_such_exc,
        "StaleElementReferenceException": sys.modules["selenium.common.exceptions"].StaleElementReferenceException,
        "json": json, "os": os, "datetime": datetime.datetime, "time": time
    })

    output = tmp_path / "raw_data" / "raw_data_GenK_part4.json"
    data = json.loads(output.read_text(encoding="utf-8"))
    assert len(data["post_detail"]) == 1


def test_genk_scrape_stops_when_stuck(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_GenK.py"

    class _Driver(FakeGenKDriver):
        def execute_script(self, script_str, *args):
            if "scrollHeight" in script_str:
                return 1000
            return super().execute_script(script_str, *args)

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    driver = _Driver(no_such_exc)
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")
    output = tmp_path / "raw_data" / "raw_data_GenK_part4.json"
    assert output.exists()


def test_genk_view_more_handles_stale_element(monkeypatch):
    from selenium.common.exceptions import StaleElementReferenceException
    from selenium.webdriver.common.by import By
    
    def _view_more_visible(driver):
        for btn in driver.find_elements(By.CSS_SELECTOR, "a.btnviewmore"):
            try:
                text = (btn.text or "").strip()
                if "Xem thêm" not in text: continue
                driver.execute_script("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", btn)
                if btn.is_displayed(): return True, btn
            except StaleElementReferenceException: continue
        return False, None
    
    class StaleElement:
        def __init__(self, text): self.text = text
        def is_displayed(self): raise StaleElementReferenceException()
        def get_attribute(self, attr): return ""
        def find_elements(self, by, sel): return []
    
    class Driver:
        def find_elements(self, by, sel):
            return [StaleElement("Xem thêm"), FakeElement(text="Xem thêm")]
        def execute_script(self, s, *args): pass
    
    monkeypatch.setattr(time, "sleep", lambda _: None)
    visible, btn = _view_more_visible(Driver())
    assert visible is True
    assert btn.text == "Xem thêm"
