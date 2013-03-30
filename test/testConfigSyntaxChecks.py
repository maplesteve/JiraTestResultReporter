from selenium import selenium
from SeleniumTest import SeleniumTest

class testConfigSyntaxChecks(SeleniumTest):

    def test_baa_SyntaxWarnings(self):
        sel = self.selenium
        # open reporterTest config page
        sel.click("link=reporterTest")
        sel.wait_for_page_to_load("30000")
        sel.click("link=Configure")
        sel.wait_for_page_to_load("30000")
        
        sel.type("name=_.projectKey", "")
        sel.focus("name=_.username")
        self.assertTrue(sel.is_text_present("You must provide a project key."))
        sel.focus("name=_.projectKey")
        sel.type("name=_.projectKey", "aaaa")
        sel.focus("name=_.username")
        self.assertFalse(sel.is_text_present("You must provide a project key."))

        sel.type("name=_.serverAddress", "")
        sel.focus("name=_.username")
        self.assertTrue(sel.is_text_present("You must provide an URL."))
        sel.focus("name=_.serverAddress")
        sel.type("name=_.serverAddress", "bbbb")
        sel.focus("name=_.username")
        self.assertTrue(sel.is_text_present("This is not a valid URL."))

        sel.focus("name=_.serverAddress")
        sel.type("name=_.serverAddress", "http://www.google.com")
        sel.focus("name=_.username")
        self.assertFalse(sel.is_text_present("This is not a valid URL."))
