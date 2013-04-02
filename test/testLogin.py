from selenium import selenium
from SeleniumTest import SeleniumTest
import unittest

class testLogin(SeleniumTest):
        
    def test_aaa_IfLoginWorks(self):
        sel = self.selenium
        sel.open("/logout")
        sel.wait_for_page_to_load("30000")
        sel.click("css=b")
        sel.wait_for_page_to_load("30000")
        sel.type("id=j_username", self.credUsername)
        sel.type("name=j_password", self.credPassword)
        sel.click("id=yui-gen1-button")
        sel.wait_for_page_to_load("30000")
        self.assertTrue(sel.is_text_present("sae | log out"))
