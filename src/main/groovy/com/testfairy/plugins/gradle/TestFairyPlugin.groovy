package com.testfairy.plugins.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class TestFairyPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

		project.configure(project) {
			if (it.hasProperty("android")) {
				tasks.whenTaskAdded { projectTask ->
					project.("android").applicationVariants.all { buildVariant ->
						// locate packageRelease and packageDebug tasks
						def expectingTask = "package${buildVariant.name.capitalize()}".toString()
						if (expectingTask.equals(projectTask.name)) {
							def variantName = buildVariant.name.capitalize()
							// create new task with name such as testfairyRelease and testfairyDebug
							def uploadTask = project.task("testfairy${variantName}", type: TestFairyUploadTask)
							uploadTask.configure {
								description = "Uploads the ${variantName} build to TestFairy"
								variant = buildVariant
								// use outputFile from packageApp task
								uploadedFile = projectTask.outputFile
								dependsOn expectingTask
							}
							project.tasks.add(uploadTask)
						}
					}
				}
			}
		}
	}
}

