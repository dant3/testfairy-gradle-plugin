package com.testfairy.plugins.gradle

class MockAndroidExtension {
	public def buildVariant = new Object() {
		public String name = 'debug'
	}

	public def applicationVariants = new Object() {
		public def all(Closure<Object> configurationHook) {
			configurationHook.call(this.buildVariant)
		}
	}
}
