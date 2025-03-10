//Runs the supplied list of commands in the specified Windows container image
//(We need to implement this ourselves since Jenkins is yet to implement support for Windows containers)
def call(image, dockerArgs, commands)
{
	//Store all environment variables starting with JENKINS_ in a file so they can be forwarded to the container
	bat 'set JENKINS_ > env.vars'
	
	//Since compiling code in a Windows container bind-mount seems to have issues,
	//we need to copy the workspace to another directory and run the commands there,
	//copying back any generated files once the commands have completed
	def targetDir = 'C:\\workspace'
	def batchCommands = [
		"@echo off",
		"(robocopy \"%WORKSPACE%\" \"${targetDir}\" /s) ^& IF %ERRORLEVEL% LSS 8 SET ERRORLEVEL = 0",
		"cd \"${targetDir}\""
	]
	
	//Halt execution if any of the user-supplied commands fails
	commands.each
	{
		batchCommands.add(it)
		batchCommands.add('if %errorlevel% neq 0 exit /b %errorlevel%')
	}
	
	//Perform the copy back into the workspace only if all commands succeeded
	batchCommands.add("(robocopy \"${targetDir}\" \"%WORKSPACE%\" /s) ^& IF %ERRORLEVEL% LSS 8 SET ERRORLEVEL = 0")
	
	//Run the container
	writeFile(file: '_______jenkins_entrypoint.bat', text: batchCommands.join('\r\n'))
	bat 'docker run --rm ' +
		'-e "WORKSPACE=%WORKSPACE%" --env-file env.vars ' +
		'-v "%WORKSPACE%:%WORKSPACE%" -w "%WORKSPACE%" ' +
		"${dockerArgs} \"${image}\" cmd /S /C _______jenkins_entrypoint.bat"
}
