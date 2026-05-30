import json
import runpy
import sys
import time
import types
from datetime import datetime
from pathlib import Path

import pytest

from conftest import FakeElement, install_fake_selenium


pytestmark = pytest.mark.scrape


class FakeVNArticle:
    def __init__(self, title, description, link):
        self.title = title
        self.description = description
        self.link = link

    def find_elements(self, by, selector):
        if selector == "h2.title-news":
            return [FakeElement(text=self.title)] if self.title is not None else []
        if selector == "h2.title-news a":
            return [FakeElement(attrs={"href": self.link})]
        return []


class FakeVNEPDriver:
    def __init__(self, with_noise=False, missing_meta=False):
        self.current_url = ""
        self.with_noise = with_noise
        self.missing_meta = missing_meta

    def get(self, url):
        self.current_url = url

    def quit(self):
        return None

    def find_element(self, by, selector):
        if selector == "a.logo":
            return FakeElement(attrs={"title": "VnExpress"})
        if by == "id" and selector == "fck_detail_gallery":
            return _FakeParagraphContainer(["Doan 1", "Doan 2"])
        raise ValueError(selector)

    def find_elements(self, by, selector):
        if selector == "article.item-news.item-news-common.thumb-left:not(.hidden)":
            if self.with_noise:
                return [
                    FakeVNArticle("Bai 1", "Mo ta 1", "https://vnexpress.net/1"),
                    FakeVNArticle(None, "Mo ta no title", "https://vnexpress.net/no-title"),
                    FakeVNArticle("Bai 2", "Mo ta 2", ""),
                    FakeVNArticle("Bai 3", "Mo ta 3", "https://vnexpress.net/eclick-ad"),
                    FakeVNArticle("Bai 1", "Mo ta 1", "https://vnexpress.net/1"),
                ]
            return [
                FakeVNArticle("Bai 1", "Mo ta 1", "https://vnexpress.net/1"),
                FakeVNArticle("Bai 2", "Mo ta 2", "https://vnexpress.net/2"),
            ]
        if selector == "span.date":
            if self.missing_meta and self.current_url.endswith("/no-title"):
                return []
            return [FakeElement(text="Thứ sáu, 6/3/2026, 07:00 (GMT+7)")]
        return []


def test_vn_express_scrape_end_to_end_basic_output(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"

    driver = FakeVNEPDriver(with_noise=False)
    install_fake_selenium(monkeypatch, driver)

    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")

    output_path = tmp_path / "raw_data" / "raw_data_VN-EP.json"
    assert output_path.exists()

    data = json.loads(output_path.read_text(encoding="utf-8"))
    assert data["source_platform"] == "VnExpress"
    assert len(data["post_detail"]) == 2
    assert all("title" in p for p in data["post_detail"])
    assert all("content" in p for p in data["post_detail"])


def test_vn_express_scrape_noise_and_missing_metadata_scenario(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"

    driver = FakeVNEPDriver(with_noise=True, missing_meta=True)
    install_fake_selenium(monkeypatch, driver)

    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")

    output_path = tmp_path / "raw_data" / "raw_data_VN-EP.json"
    data = json.loads(output_path.read_text(encoding="utf-8"))

    assert len(data["post_detail"]) == 2
    titles = [item["title"] for item in data["post_detail"]]
    assert "Bai 1" in titles
    assert "" in titles


class _FakeParagraphContainer:
    def __init__(self, texts):
        self.texts = texts

    def find_elements(self, by, selector):
        if selector == "p.Normal":
            return [FakeElement(text=t) for t in self.texts]
        return []


def test_vn_express_all_missing_content_returns_empty_post_detail(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"
    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)

    class _Driver(FakeVNEPDriver):
        def find_element(self, by, selector):
            if selector == "a.logo":
                return FakeElement(attrs={"title": "VnExpress"})
            if by == "id" and selector == "fck_detail_gallery":
                raise no_such_exc(selector)
            raise ValueError(selector)

    driver = _Driver(with_noise=False)
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")

    output = tmp_path / "raw_data" / "raw_data_VN-EP.json"
    data = json.loads(output.read_text(encoding="utf-8"))
    assert data["post_detail"] == []


def test_vn_express_metadata_and_datetime_format(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"

    driver = FakeVNEPDriver(with_noise=False)
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")

    output = tmp_path / "raw_data" / "raw_data_VN-EP.json"
    data = json.loads(output.read_text(encoding="utf-8"))

    assert data["source_platform"] == "VnExpress"
    assert data["source_url"] == "https://vnexpress.net/khoa-hoc-cong-nghe/ai"
    datetime.strptime(data["scraped_at"], "%Y-%m-%d %H:%M:%S")


def test_vn_express_joins_paragraphs_with_double_newline(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"

    class _Driver(FakeVNEPDriver):
        def find_element(self, by, selector):
            if selector == "a.logo":
                return FakeElement(attrs={"title": "VnExpress"})
            if by == "id" and selector == "fck_detail_gallery":
                return _FakeParagraphContainer(["Doan 1", "Doan 2"])
            raise ValueError(selector)

    driver = _Driver(with_noise=False)
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")

    output = tmp_path / "raw_data" / "raw_data_VN-EP.json"
    data = json.loads(output.read_text(encoding="utf-8"))
    assert data["post_detail"][0]["content"] == "Doan 1\n\nDoan 2"


def test_vn_express_builds_pagination_urls_correctly(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"

    class _Driver(FakeVNEPDriver):
        def __init__(self):
            super().__init__(with_noise=False)
            self.visited_urls = []

        def get(self, url):
            self.current_url = url
            self.visited_urls.append(url)

        def find_element(self, by, selector):
            if selector == "a.logo":
                return FakeElement(attrs={"title": "VnExpress"})
            if by == "id" and selector == "fck_detail_gallery":
                return _FakeParagraphContainer(["Noi dung 1", "Noi dung 2"])
            raise ValueError(selector)

    driver = _Driver()
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    runpy.run_path(str(script), run_name="__main__")

    base_url = "https://vnexpress.net/khoa-hoc-cong-nghe/ai"
    assert base_url in driver.visited_urls
    assert "https://vnexpress.net/khoa-hoc-cong-nghe/ai-p2" in driver.visited_urls
    assert "https://vnexpress.net/khoa-hoc-cong-nghe/ai-p3" in driver.visited_urls


def test_vn_express_whitespace_stripping(tmp_path, monkeypatch):
    """Test that VN-Express scraper strips whitespace-only paragraphs."""
    root = Path(__file__).resolve().parents[2]
    script = root / "src" / "data-pipeline" / "scrape_from_VN-EP.py"

    class _Driver(FakeVNEPDriver):
        def find_element(self, by, selector):
            if selector == "fck_detail_gallery":
                elem = FakeElement("")
                # Diverse whitespace cases: space, tab, newline
                elem._elements["p.Normal"] = [
                    FakeElement("  "), 
                    FakeElement("\t"),
                    FakeElement("\n"),
                    FakeElement("Valid Content"),
                    FakeElement("  \t\n  ")
                ]
                return elem
            return super().find_element(by, selector)

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    driver = _Driver()
    install_fake_selenium(monkeypatch, driver)
    monkeypatch.setattr(time, "sleep", lambda *_: None)
    monkeypatch.chdir(tmp_path)

    # Mock num_pages = 1
    script_content = script.read_text(encoding="utf-8").replace("num_pages = 10", "num_pages = 1")
    
    exec(script_content, {
        "__name__": "__main__",
        "webdriver": sys.modules["selenium.webdriver"],
        "By": sys.modules["selenium.webdriver.common.by"].By,
        "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
        "json": json, "os": sys.modules["os"], "datetime": datetime, "time": time
    })
    
    output = tmp_path / "raw_data" / "raw_data_VN-EP.json"
    data = json.loads(output.read_text(encoding="utf-8"))
    assert data["post_detail"][0]["content"] == "Valid Content"