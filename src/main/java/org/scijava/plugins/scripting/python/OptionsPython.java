/*-
 * #%L
 * Python scripting language plugin to be used via scyjava.
 * %%
 * Copyright (C) 2021 - 2025 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.plugins.scripting.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.scijava.app.AppService;
import org.scijava.command.CommandService;
import org.scijava.launcher.Config;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.options.OptionsPlugin;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.TextWidget;

/**
 * Options for configuring the Python environment.
 *
 * @author Curtis Rueden
 */
@Plugin(type = OptionsPlugin.class, menu = { @Menu(
	label = MenuConstants.EDIT_LABEL, weight = MenuConstants.EDIT_WEIGHT,
	mnemonic = MenuConstants.EDIT_MNEMONIC), @Menu(label = "Options",
		mnemonic = 'o'), @Menu(label = "Python...", weight = 10), })
public class OptionsPython extends OptionsPlugin {

	// -- Dependency constants and fields --
	private static final String DEFAULT_PYIMAGEJ = "pyimagej>=1.7.0";
	private static final String DEFAULT_APPOSE = "appose>=0.7.2";

	@Parameter
	private AppService appService;

	@Parameter
	private CommandService commandService;

	@Parameter
	private LogService log;

	@Parameter(label = "Python environment directory", persist = false)
	private File pythonDir;

	@Parameter(label = "Conda dependencies", style = TextWidget.AREA_STYLE,
		persist = false)
	private String condaDependencies;

	@Parameter(label = "Pip dependencies", style = TextWidget.AREA_STYLE,
		persist = false)
	private String pipDependencies;

	@Parameter(label = "Build Python environment", callback = "rebuildEnv")
	private Button rebuildEnvironment;

	@Parameter(label = "Launch in Python mode", callback = "updatePythonConfig",
		persist = false)
	private boolean pythonMode;

	@Parameter(required = false)
	private UIService uiService;

	// -- Private fields --

	// These hold the parsed or user-specified values, or null if not found
	private String pyimagejDependency;
	private String apposeDependency;

	private boolean initialPythonMode = false;
	private String initialCondaDependencies;
	private String initialPipDependencies;

	// -- OptionsPython methods --

	public File getPythonDir() {
		return pythonDir;
	}

	public boolean isPythonMode() {
		return pythonMode;
	}

	public void setPythonDir(final File pythonDir) {
		if (pythonDir == null) {
			this.pythonDir = null;
		} else {
			// Trim whitespace and remove illegal characters from the path
			String path = pythonDir.getPath();
			if (path != null) {
				// Remove leading/trailing whitespace and angle brackets
				path = path.trim().replaceAll("^[\\s<>]+|[\\s<>]+$", "");
				// Remove illegal characters for Windows paths except : after drive letter
				// Remove all colons except at index 1 (C:)
				if (path.length() > 2 && path.charAt(1) == ':') {
					path = path.substring(0, 2) + path.substring(2).replace(":", "");
				} else {
					path = path.replace(":", "");
				}
				// Remove any other illegal characters (keep it simple)
				path = path.replaceAll("[\n\r\t]", "");
			}
			this.pythonDir = new File(path);
		}
	}

	public void setPythonMode(final boolean pythonMode) {
		this.pythonMode = pythonMode;
	}

	// -- Callback methods --

	@Override
	public void load() {
		// Read python-dir and launch-mode from app config file.
		String configFileProp = System.getProperty("scijava.app.config-file");
		File configFile = configFileProp == null ? null : new File(configFileProp);
		if (configFile != null && configFile.canRead()) {
			try {
				final Map<String, String> config = Config.load(configFile);

				final String cfgPythonDir = config.get("python-dir");
				if (cfgPythonDir != null) {
					final Path appPath = appService.getApp().getBaseDirectory().toPath();
					pythonDir = stringToFile(appPath, cfgPythonDir);
				}

				final String cfgLaunchMode = config.get("launch-mode");
				if (cfgLaunchMode != null) pythonMode = cfgLaunchMode.equals("PYTHON");
			}
			catch (IOException e) {
				// Proceed gracefully if config file is not accessible.
				log.debug(e);
			}
		}

		if (pythonDir == null) {
			// For the default Python directory, try to match the platform
			// string used for Java installations.
			final String javaPlatform = System.getProperty(
				"scijava.app.java-platform");
			final String platform = javaPlatform != null ? javaPlatform : System
				.getProperty("os.name") + "-" + System.getProperty("os.arch");
			final Path pythonPath = appService.getApp().getBaseDirectory().toPath()
				.resolve("python").resolve(platform);
			pythonDir = pythonPath.toFile();
		}

		// Store the initial value of pythonMode for later comparison
		initialPythonMode = pythonMode;

		// Populate condaDependencies and pipDependencies from environment.yml
		condaDependencies = "";
		pipDependencies = "";
		pyimagejDependency = null;
		apposeDependency = null;
		java.util.Set<String> pipBlacklist = new java.util.HashSet<>();
		pipBlacklist.add("appose");
		pipBlacklist.add("pyimagej");
		File envFile = getEnvironmentYamlFile();
		if (envFile.exists()) {
			try {
				java.util.List<String> lines = java.nio.file.Files.readAllLines(envFile
					.toPath());
				boolean inDeps = false, inPip = false;
				StringJoiner condaDeps = new StringJoiner("\n");
				StringJoiner pipDeps = new StringJoiner("\n");
				for (String line : lines) {
					String trimmed = line.trim();
					if (trimmed.startsWith("#") || trimmed.isEmpty()) {
						// Ignore empty and comment lines
						continue;
					}
					if (trimmed.startsWith("dependencies:")) {
						inDeps = true;
						continue;
					}
					if (inDeps && trimmed.startsWith("- pip")) {
						inPip = true;
						continue;
					}
					if (inDeps && trimmed.startsWith("- ") && !inPip) {
						String dep = trimmed.substring(2).trim();
						if (!dep.equals("pip")) condaDeps.add(dep);
						continue;
					}
					if (inPip && trimmed.startsWith("- ")) {
						String pipDep = trimmed.substring(2).trim();
						if (pipDep.startsWith("pyimagej")) pyimagejDependency = pipDep;
						else if (pipDep.contains("appose")) apposeDependency = pipDep;
						else {
							boolean blacklisted = false;
							for (String bad : pipBlacklist) {
								if (pipDep.contains(bad)) {
									blacklisted = true;
									break;
								}
							}
							if (!blacklisted) pipDeps.add(pipDep);
						}
						continue;
					}
					if (inDeps && !trimmed.startsWith("- ") && !trimmed.isEmpty())
						inDeps = false;
					if (inPip && (!trimmed.startsWith("- ") || trimmed.isEmpty())) inPip =
						false;
				}
				condaDependencies = condaDeps.toString().trim();
				pipDependencies = pipDeps.toString().trim();
				initialCondaDependencies = condaDependencies;
				initialPipDependencies = pipDependencies;
			}
			catch (Exception e) {
				log.debug("Could not read environment.yml: " + e.getMessage());
			}
		}
	}

	public void rebuildEnv() {
		File environmentYaml = writeEnvironmentYaml();
		commandService.run(RebuildEnvironment.class, true, "environmentYaml",
			environmentYaml, "targetDir", pythonDir);
	}

	/**
	 * Returns the File for the environment.yml, using the system property if set.
	 */
	private File getEnvironmentYamlFile() {
		final Path appPath = appService.getApp().getBaseDirectory().toPath();
		File environmentYaml = appPath.resolve("config").resolve("environment.yml")
			.toFile();
		final String pythonEnvFileProp = System.getProperty(
			"scijava.app.python-env-file");
		if (pythonEnvFileProp != null) {
			environmentYaml = stringToFile(appPath, pythonEnvFileProp);
		}
		return environmentYaml;
	}

	@Override
	public void save() {
		setPythonDir(pythonDir); // clean up the path
		// Write python-dir and launch-mode values to app config file.
		final String configFileProp = System.getProperty("scijava.app.config-file");
		if (configFileProp == null) return; // No config file to update.
		final File configFile = new File(configFileProp);
		Map<String, String> config = null;
		if (configFile.isFile()) {
			try {
				config = Config.load(configFile);
			}
			catch (IOException exc) {
				// Proceed gracefully if config file is not accessible.
				log.debug(exc);
			}
		}
		if (config == null) config = new LinkedHashMap<>();
		final Path appPath = appService.getApp().getBaseDirectory().toPath();
		config.put("python-dir", fileToString(appPath, pythonDir));
		config.put("launch-mode", pythonMode ? "PYTHON" : "JVM");
		try {
			Config.save(configFile, config);
		}
		catch (IOException exc) {
			// Proceed gracefully if config file cannot be written.
			log.debug(exc);
		}

		if (pythonMode && (pythonDir == null || !pythonDir.exists())) {
			rebuildEnv();
		}
		else {
			writeEnvironmentYaml();
		}
		// Warn the user if pythonMode was just enabled and wasn't before.
		if (!initialPythonMode && pythonMode && uiService != null) {
			String msg =
				"You have just enabled Python mode. Please restart for these changes to take effect\n" +
				"(once your Python environment has finished initializing).\n\n" +
				"If Fiji fails to start, try deleting your configuration file and restarting.\n\n" +
				"Configuration file: " + configFile;
			uiService.showDialog(msg, "Python Mode Enabled",
				DialogPrompt.MessageType.WARNING_MESSAGE);
		}
	}

	private File writeEnvironmentYaml() {
		File envFile = getEnvironmentYamlFile();

		// skip writing if nothing has changed
		if (initialCondaDependencies.equals(condaDependencies) &&
			initialPipDependencies.equals(pipDependencies)) return envFile;

		// Update initial dependencies to detect future changes
		initialCondaDependencies = condaDependencies;
		initialPipDependencies = pipDependencies;

		// Write environment.yml from condaDependencies and pipDependencies
		try {
			String name = "fiji";
			String[] channels = { "conda-forge" };
			StringBuilder yml = new StringBuilder();
			yml.append("name: ").append(name).append("\nchannels:\n");
			for (String ch : channels)
				yml.append("  - ").append(ch).append("\n");
			yml.append("dependencies:\n");
			for (String dep : condaDependencies.split("\n")) {
				String trimmed = dep.trim();
				if (!trimmed.isEmpty()) yml.append("  - ").append(trimmed).append("\n");
			}
			yml.append("  - pip\n");
			yml.append("  - pip:\n");
			boolean foundPyimagej = false, foundAppose = false;
			for (String dep : pipDependencies.split("\n")) {
				String trimmed = dep.trim();
				if (!trimmed.isEmpty()) {
					if (trimmed.startsWith("pyimagej")) foundPyimagej = true;
					if (trimmed.contains("appose")) foundAppose = true;
					yml.append("    - ").append(trimmed).append("\n");
				}
			}
			// Append pyimagej if not found
			if (!foundPyimagej) {
				String pyimagej = pyimagejDependency != null ?
					pyimagejDependency : DEFAULT_PYIMAGEJ;
				yml.append("    - ").append(pyimagej).append("\n");
			}
			// Append appose if not found
			if (!foundAppose) {
				String appose = apposeDependency != null
					? apposeDependency : DEFAULT_APPOSE;
				yml.append("    - ").append(appose).append("\n");
			}
			java.nio.file.Files.write(envFile.toPath(), yml.toString().getBytes());
			pyimagejDependency = null;
			apposeDependency = null;
		}
		catch (Exception e) {
			log.debug("Could not write environment.yml: " + e.getMessage());
		}
		return envFile;
	}

	// -- Utility methods --

	/**
	 * Converts a path string to a file, treating relative path expressions as
	 * relative to the given base directory, not the current working directory.
	 */
	static File stringToFile(Path baseDir, String value) {
		final Path path = Paths.get(value);
		final Path absPath = path.isAbsolute() ? path : baseDir.resolve(path);
		return absPath.toFile();
	}

	/**
	 * Converts a file to a path string, which in the case of a file beneath the
	 * given base directory, will be a path expression relative to that base.
	 */
	static String fileToString(Path baseDir, File file) {
		Path filePath = file.toPath();
		Path relPath = filePath.startsWith(baseDir) ? baseDir.relativize(filePath)
			: filePath.toAbsolutePath();
		return relPath.toString();
	}
}
