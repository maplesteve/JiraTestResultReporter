from selenium import selenium
from SeleniumTest import SeleniumTest
import pprint

class testConfigChanges(SeleniumTest):

    def test_caa_ConfigChanges(self):
        sel = self.selenium
        
        self.open_reporterTest_page()
        self.open_config_page()
        self.MakeConfigChanges()
        self.save_config()
        self.open_config_page()
        self.CheckConfig()
        
    def MakeConfigChanges(self):
        sel = self.selenium
        sel.type("name=_.projectKey", self.newProjectKey)
        sel.type("name=_.serverAddress", self.newServerAddress)
        sel.type("name=_.username", self.newUsername)
        sel.type("name=_.password", self.newPassword)
        sel.check("name=_.createAllFlag")
#         pprint.pprint(sel.get_value("name=_.debugFlag"))
#         pprint.pprint(sel.get_value("name=_.verboseDebugFlag"))
        sel.uncheck("name=_.debugFlag")
        sel.check("name=_.verboseDebugFlag")
#         pprint.pprint(sel.get_value("name=_.debugFlag"))
#         pprint.pprint(sel.get_value("name=_.verboseDebugFlag"))
        
    def CheckConfig(self):
        sel = self.selenium
        self.assertEqual(self.newProjectKey, sel.get_value("name=_.projectKey"))
        correctedServerAddress = ''.join([self.newServerAddress, "/"])
        self.assertEqual(correctedServerAddress, sel.get_value("name=_.serverAddress"))
        self.assertEqual(self.newUsername, sel.get_value("name=_.username"))
        self.assertEqual(self.newPassword, sel.get_value("name=_.password"))
        self.assertEqual(sel.get_value("name=_.createAllFlag"), 'on')
#         pprint.pprint(sel.get_value("name=_.debugFlag"))
#         pprint.pprint(sel.get_value("name=_.verboseDebugFlag"))
        self.assertEqual(sel.get_value("name=_.debugFlag"), 'on')
        self.assertEqual(sel.get_value("name=_.verboseDebugFlag"), 'on')
    
    def open_reporterTest_page(self):
        sel = self.selenium
        sel.click("link=reporterTest")
        sel.wait_for_page_to_load("30000")
    
    def open_config_page(self):
        sel = self.selenium
        sel.click("link=Configure")
        sel.wait_for_page_to_load("30000")
        
    def save_config(self):
        sel = self.selenium
        sel.click("id=yui-gen35-button")
        sel.wait_for_page_to_load("30000")



        
