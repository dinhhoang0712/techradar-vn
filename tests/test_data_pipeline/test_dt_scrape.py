import json
import os
import sys
from pathlib import Path
import time

import pytest
from conftest import FakeElement, install_fake_selenium, NoSuchElementException

pytestmark = pytest.mark.scrape

class FakeDTDriver:
    def __init__(self, no_such_exc):
        self.no_such_exc = no_such_exc
        self.current_url = ""
        self.visited_urls = []
    
    def get(self, url):
        self.current_url = url
        self.visited_urls.append(url)
    
    def find_element(self, by, selector):
        if selector == "a[aria-label]":
            return FakeElement(text="Dân trí", attrs={"aria-label": "Dân trí - Tin tức mới nhất"})
        
        if selector == "desktop-in-article":
            if "article-fail" in self.current_url:
                raise self.no_such_exc("Article content not found")
            elem = FakeElement(text="")
            elem._elements["p"] = [FakeElement(text="Paragraph 1"), FakeElement(text="Paragraph 2")]
            return elem
        
        if selector == 'a[data-prop="sapo"]':
             return FakeElement(text="", attrs={"href": "https://dantri.com.vn/job-default"})

        raise self.no_such_exc(selector)

    def find_elements(self, by, selector):
        if selector == "div.article-content":
            # Normal article
            art1 = FakeElement(text="")
            art1._elements['a[data-prop="sapo"]'] = [FakeElement(text="", attrs={"href": "https://dantri.com.vn/article-1"})]
            art1._elements['h3.article-title'] = [FakeElement(text="Title 1")]
            
            # Duplicate of article 1
            art2 = FakeElement(text="")
            art2._elements['a[data-prop="sapo"]'] = [FakeElement(text="", attrs={"href": "https://dantri.com.vn/article-1"})]
            art2._elements['h3.article-title'] = [FakeElement(text="Title 1 Duplicate")]

            # Ad link
            art3 = FakeElement(text="")
            art3._elements['a[data-prop="sapo"]'] = [FakeElement(text="", attrs={"href": "https://eclick.vn/ad-link"})]
            art3._elements['h3.article-title'] = [FakeElement(text="Ad Title")]

            # Article that will fail content extraction
            art_fail = FakeElement(text="")
            art_fail._elements['a[data-prop="sapo"]'] = [FakeElement(text="", attrs={"href": "https://dantri.com.vn/article-fail"})]
            art_fail._elements['h3.article-title'] = [FakeElement(text="Title Fail")]

            if "trang-2" in self.current_url:
                return []
            
            return [art1, art2, art3, art_fail]
        
        if selector == "p":
            return [FakeElement(text="Paragraph 1"), FakeElement(text="Paragraph 2")]
        
        return []

    def quit(self): pass

def test_dt_scrape_all_parts(tmp_path, monkeypatch):
    """Combined test for DT scraper covering both part 1 and part 2."""
    root = Path(__file__).resolve().parents[2]
    script_path = root / "src" / "data-pipeline" / "scrape_from_DT.py"

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    driver = FakeDTDriver(no_such_exc)
    install_fake_selenium(monkeypatch, driver)
    
    monkeypatch.setattr(time, "sleep", lambda _: None)
    monkeypatch.chdir(tmp_path)
    
    script_content = script_path.read_text(encoding="utf-8")
    
    for p in [1, 2]:
        # Reset raw_data dir
        raw_dir = tmp_path / "raw_data"
        if raw_dir.exists():
            for f in raw_dir.glob("*"): f.unlink()
        
        # Override part and minimize pages
        content = script_content.replace("num_pages = 10", "num_pages = 1")
        content = content.replace("part = 2", f"part = {p}")
        
        exec(content, {
            "__name__": "__main__", 
            "webdriver": sys.modules["selenium.webdriver"],
            "By": sys.modules["selenium.webdriver.common.by"].By,
            "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
            "NoSuchElementException": no_such_exc,
            "json": json, "os": os, "datetime": sys.modules["datetime"].datetime,
            "time": time
        })
        
        output = tmp_path / "raw_data" / f"raw_data_DT_part{p}.json"
        assert output.exists(), f"Part {p} output file missing"
        
        data = json.loads(output.read_text(encoding="utf-8"))
        assert data["source_platform"] == "Dân trí - Tin tức mới nhất"
        assert len(data["post_detail"]) == 1
        assert data["post_detail"][0]["title"] == "Title 1"
