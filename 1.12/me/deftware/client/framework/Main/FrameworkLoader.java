package me.deftware.client.framework.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import me.deftware.client.framework.FrameworkConstants;
import me.deftware.client.framework.Client.EMCClient;
import me.deftware.client.framework.FontRender.Fonts;
import net.minecraft.client.Minecraft;

public class FrameworkLoader {
	
	public static Logger logger = LogManager.getLogger();
	
	private static URLClassLoader clientLoader;

	/**
	 * Info about the loaded clients
	 */
	public static ArrayList<JsonObject> modsInfo = new ArrayList<JsonObject>();

	/**
	 * Our client instances
	 */
	private static HashMap<String, EMCClient> mods = new HashMap<String, EMCClient>();
	
	/**
	 * Called from the Minecraft initialization method
	 */
	public static void init() {
		try {
			logger.info("Loading EMC...");
			
			// Initialize framework stuff
			Fonts.loadFonts();

			File minecraft = new File(
					Minecraft.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			File mods = new File(minecraft.getParent() + File.separator + "mods");

			if (!mods.exists()) {
				mods.mkdir();
			}

			for (final File fileEntry : mods.listFiles()) {
				if (fileEntry.isDirectory()) {
					continue;
				}
				if (fileEntry.getName().endsWith(".jar")) {
					loadClient(fileEntry);
				}
			}

			loadClient(new File(minecraft.getParent() + File.separator + "Client.jar"));
			
		} catch (Exception ex) {
			logger.warn("Failed to load some EMC mods");
			ex.printStackTrace();
		}
	}

	private static void loadClient(File clientJar) throws Exception {
		// Find the client jar

		if (!clientJar.exists()) {
			throw new Exception("Specified mod jar not found");
		}

		// Load client

		JarFile jarFile = new JarFile(clientJar);
		Enumeration e = jarFile.entries();

		URL jarfile = new URL("jar", "", "file:" + clientJar.getAbsolutePath() + "!/");
		clientLoader = URLClassLoader.newInstance(new URL[] { jarfile });

		// Read client.json

		InputStream in = clientLoader.getResourceAsStream("client.json");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuilder result = new StringBuilder("");

		String line;
		while ((line = reader.readLine()) != null) {
			result.append(line);
		}
		in.close();

		JsonObject jsonObject = new Gson().fromJson(result.toString(), JsonObject.class);
		modsInfo.add(jsonObject);

		logger.info("Loading mod: " + jsonObject.get("name").getAsString() + " by "
				+ jsonObject.get("author").getAsString());

		if (jsonObject.get("minversion").getAsInt() > FrameworkConstants.VERSION) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiUpdateLoader(jsonObject));
			jarFile.close();
			return;
		}

		mods.put(jsonObject.get("name").getAsString(),
				(EMCClient) clientLoader.loadClass(jsonObject.get("main").getAsString()).newInstance());

		for (JarEntry je = (JarEntry) e.nextElement(); e.hasMoreElements(); je = (JarEntry) e.nextElement()) {
			if (je.isDirectory() || !je.getName().endsWith(".class")) {
				continue;
			}
			String className = je.getName().replace(".class", "").replace('/', '.');
			logger.info("Loaded class " + clientLoader.loadClass(className).getName());
		}

		jarFile.close();

		mods.get(jsonObject.get("name").getAsString()).init(jsonObject);

		logger.info("Loaded mod");
	}

	public static HashMap<String, EMCClient> getClients() {
		return mods;
	}

	/**
	 * Unloads the client
	 */
	public static void ejectClients() {
		mods.clear();
	}
	
}
