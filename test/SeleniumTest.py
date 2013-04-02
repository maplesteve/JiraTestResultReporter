import unittest, random
from selenium import selenium

class SeleniumTest(unittest.TestCase):
    def setUp(self):
        self.makeConfigStrings()
        self.loadCredentials()
        self.verificationErrors = []
        self.selenium = selenium(credentials.seleniumHost, credentials.seleniumPort, "*firefox", credentials.seleniumBaseURL)
        self.selenium.start()

    def tearDown(self):
        self.selenium.stop()
        self.assertEqual([], self.verificationErrors)
        
    def makeConfigStrings(self):
        randomString = ''.join([random.choice('abcdefghijklmnoprstuvwyxzABCDEFGHIJKLMNOPRSTUVWXYZ0123456789') for i in range(8)])
        self.newProjectKey = ''.join([randomString, '-projectKey'])
        self.newServerAddress = ''.join([randomString, '-serverAddress'])
        self.newUsername = ''.join([randomString, '-username'])
        self.newPassword = ''.join([randomString, '-password'])
        
    def loadCredentials(self):
        import imp
        f = open('myCredentials.txt')
        global credentials
        credentials = imp.load_source('credentials', '', f)
        f.close()
        self.credUsername = credentials.username
        self.credPassword = credentials.password
