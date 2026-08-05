package playerpi.piscripter.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import playerpi.piscripter.PiScripter;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import com.google.gson.Gson;


import static playerpi.piscripter.PiScripter.LOGGER;

public class PiScripterClient implements ClientModInitializer {

	//String SCRIPTS_FOLDER_PATH = "..\\src\\main\\resources\\assets\\piscripter\\scripts\\"; // FIXME: should be somewhere else than in that asset folder (in the main .minecraft folder? It wouldn't break when building the mod (I don't know if it already does) and it would be easier for players to access.)

	@Override
	public void onInitializeClient() {

		Path scriptFolder = new Script("").MAIN_FOLDER;
		if (!Files.exists(scriptFolder)) {
            try {
                Files.createDirectory(scriptFolder);
				LOGGER.info("Main 'scripts' folder not found. Created the directory.");
            } catch (IOException e) {
                LOGGER.error("Could not create scripts directory: ", e);
            }
        }

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundRunCommandPayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			runCommand(payload.command());
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundNewFilePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			Script script = new Script(payload.fileName());
			script.createScriptFile();
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundScriptDuplicateFilePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}

			String[] fileNameAndNewNameSplit = {"", ""}; // TODO make this less convoluted
			int index = 0;
			for (char character : payload.fileNameAndNewName().toCharArray()) {
				if (character == '|') {
					if (++index == 2) { break; }
				} else {
					fileNameAndNewNameSplit[index] += character;
				}
			}
			String fileName = fileNameAndNewNameSplit[0];
			String newFileName = fileNameAndNewNameSplit[1];

			Script originalScript = new Script(fileName);
			Script newScript = new Script(newFileName);

			if (newScript.exists()) {
				PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload("File " + newFileName + " already exists. Try using another name or deleting this one.");
				ClientPlayNetworking.send(payloadBack);
			} else {
				newScript.createScriptFile();
				newScript.setInfoFileValue("description", "Copy of " + fileName);
				newScript.setInfoFileValue("authors", originalScript.getInfoAuthors());

                try {
                    Files.copy(originalScript.getScriptFile(), newScript.getScriptFile(), StandardCopyOption.REPLACE_EXISTING);
					Paths.get(newScript.MAIN_PATH + "\\" + newFileName + "\\" + fileName).toFile().renameTo(new File(newScript.MAIN_PATH + "\\" + newFileName + "\\" + newFileName));
                } catch (IOException e) {
                    LOGGER.error("Could not copy script file: ", e);
                }
            }
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundScriptRenameFilePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}

			String[] fileNameAndNewNameSplit = {"", ""}; // TODO make this less convoluted
			int index = 0;
			for (char character : payload.fileNameAndNewName().toCharArray()) {
				if (character == '|') {
					if (++index == 2) { break; }
				} else {
					fileNameAndNewNameSplit[index] += character;
				}
			}
			String oldFileName = fileNameAndNewNameSplit[0];
			String newFileName = fileNameAndNewNameSplit[1];

			Script script = new Script(oldFileName);
			Script scriptNewName = new Script(newFileName);

			if (scriptNewName.exists()) {
				PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload("File " + newFileName + " already exists. Try using another name or deleting this one.");
				ClientPlayNetworking.send(payloadBack);
			} else {
				script.setInfoFileValue("name", newFileName);
				script.getScriptFile().toFile().renameTo(new File(script.MAIN_PATH + "\\" + oldFileName + "\\" + newFileName));
				script.getScriptFolder().toFile().renameTo(new File(script.MAIN_PATH + "\\" + newFileName));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundGetScriptsPayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			if (payload.sayInChat()){
				Path folder = new Script("").MAIN_FOLDER;
				String scripts = "";
				for (Script script : listScriptsFromFolder(folder)) {
					scripts += script.fileName + ", ";
				}
				if (scripts.length() > 1) {
					PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload(scripts.substring(0, scripts.length() - 2));
					ClientPlayNetworking.send(payloadBack);
				} else {
					PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload("");
					ClientPlayNetworking.send(payloadBack);
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundListBreakpointsPayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload( new Script(payload.fileName()).getInfoBreakpointsString() );
			ClientPlayNetworking.send(payloadBack);
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundDeleteFilePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			new Script(payload.fileName()).delete();
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundOpenFolderPayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			Script script = new Script(payload.script());
			script.openFolder();
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundSetScriptLinePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			String fileNameLineAndExpression = payload.fileNameLineAndExpression();

			String[] fileNameLineAndExpressionSplit = {"","",""}; // TODO make this less convoluted
			int index = 0;
			for (char character : fileNameLineAndExpression.toCharArray()) {
				if (character == '|'){
					if (++index == 3) { break; }
				} else {
					fileNameLineAndExpressionSplit[index] += character;
				}
			}
			String fileName = fileNameLineAndExpressionSplit[0];
			int lineIndex = Integer.parseInt(fileNameLineAndExpressionSplit[1]);
			String expression = fileNameLineAndExpressionSplit[2];
			Script script = new Script(fileName);
			script.setLine(lineIndex, expression);
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundScriptInsertLinePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			String fileNameLineAndExpression = payload.fileNameLineAndExpression();

			String[] fileNameLineAndExpressionSplit = {"","",""}; // TODO make this less convoluted
			int index = 0;
			for (char character : fileNameLineAndExpression.toCharArray()) {
				if (character == '|'){
					if (++index == 3) { break; }
				} else {
					fileNameLineAndExpressionSplit[index] += character;
				}
			}
			String fileName = fileNameLineAndExpressionSplit[0];
			int lineIndex = Integer.parseInt(fileNameLineAndExpressionSplit[1]);
			String expression = fileNameLineAndExpressionSplit[2];
			Script script = new Script(fileName);
			script.insertLine(lineIndex, expression);
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundScriptRemoveLinesPayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			String fileNameLineAndExpression = payload.fileNameAndLines();

			String[] fileNameAndLinesSplit = {"","",""}; // TODO make this less convoluted
			int index = 0;
			for (char character : fileNameLineAndExpression.toCharArray()) {
				if (character == '|'){
					if (++index == 3) { break; }
				} else {
					fileNameAndLinesSplit[index] += character;
				}
			}
			String fileName = fileNameAndLinesSplit[0];
			int lineIndexMin = Integer.parseInt(fileNameAndLinesSplit[1]);
			int lineIndexMax = Integer.parseInt(fileNameAndLinesSplit[2]);
			Script script = new Script(fileName);
			script.removeLines(lineIndexMin, lineIndexMax);
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundScriptAddLinePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			String fileNameLineAndExpression = payload.fileNameAndExpression();

			String[] fileNameAndExpressionSplit = {"",""}; // TODO make this less convoluted
			int index = 0;
			for (char character : fileNameLineAndExpression.toCharArray()) {
				if (character == '|'){
					if (++index == 2) { break; }
				} else {
					fileNameAndExpressionSplit[index] += character;
				}
			}
			String fileName = fileNameAndExpressionSplit[0];
			String expression = fileNameAndExpressionSplit[1];
			Script script = new Script(fileName);
			script.addLine(expression);
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundReadFilePayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}

			String[] fileNameAndLineIndexesSplit = {"","",""}; // TODO make this less convoluted
			int index = 0;
			for (char character : payload.fileNameAndLineIndexes().toCharArray()) {
				if (character == '|'){
					index++;
				} else {
					fileNameAndLineIndexesSplit[index] += character;
				}
			}
			String fileName = fileNameAndLineIndexesSplit[0];
			int lineMin = Integer.parseInt(fileNameAndLineIndexesSplit[1]);
			int lineMax = Integer.parseInt(fileNameAndLineIndexesSplit[2]);
			Script script = new Script(fileName);
			String fileContents = script.readScriptFile(lineMin,lineMax,true);
			PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload(fileContents);
			ClientPlayNetworking.send(payloadBack);
		});

		ClientPlayNetworking.registerGlobalReceiver(PiScripter.ClientboundGetInfoPayload.TYPE, (payload, context) -> {
			if (context.client().level == null) {return;}
			Script script = new Script(payload.fileName());
			if (Files.exists(script.getInfoFile())) {
				String infoContents =
						"Name: " + script.getInfoName()
						+ "\nDescription: " + script.getInfoDescription()
						+ "\nGame Version: " + script.getInfoGameVersion()
						+ "\nLanguage Version: " + script.getInfoLanguageVersion()
						+ "\nAuthors: " + script.getInfoAuthorsString()
						+ "\nDate of Creation: " + script.getInfoDateOfCreation();

				PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload(infoContents);
				ClientPlayNetworking.send(payloadBack);
			} else {

				PiScripter.ServerboundShowResultMessagePayload payloadBack = new PiScripter.ServerboundShowResultMessagePayload(""); // Brings error
				ClientPlayNetworking.send(payloadBack);
			}
		});
	}

	public void runCommand(String command) {
		Minecraft.getInstance().getConnection().sendCommand(command);
	}

	public void sendChat(String message) { //this is the player sending the message
		ExecuteCode ExecuteCode = new ExecuteCode();
		ExecuteCode.start();
		Minecraft.getInstance().getConnection().sendChat(message);
	}


	public static class ScriptInfo {
		public String name = "unnamed_script";
		public String language_version = "1.0";
		public String game_version = "26.2";
		public String description = "";
		public String date_of_creation = LocalDate.now().toString();
		public String[] authors = {Minecraft.getInstance().getUser().getName()};
		public int[] breakpoints = {};
	}

	public class JsonHandler {
		private static final Gson GSON = new Gson();
		public static ScriptInfo loadConfig(Path path) {
			if (!Files.exists(path)) {
				LOGGER.error("Could not find info file! Returned default values");
				return new ScriptInfo();
			}
			try (FileReader reader = new FileReader(path.toFile())) {
				return GSON.fromJson(reader, ScriptInfo.class);
			} catch (IOException e) {
				LOGGER.error("Unexpected error: ", e);
				return new ScriptInfo();
			}
		}
		public static void setGsonConfig(Path path, String id, Object newValue) {
			if (!Files.exists(path)) {
				LOGGER.error("Could not find info file!");
				return;
			}
			try {
				JsonObject jsonObject;
				try (FileReader reader = new FileReader(path.toFile())) {
					jsonObject = GSON.fromJson(reader, JsonObject.class);
					if (jsonObject == null) {
						jsonObject = new JsonObject();
					}
				}

				JsonElement newValueTree = GSON.toJsonTree(newValue);
				jsonObject.add(id, newValueTree);

				try (FileWriter writer = new FileWriter(path.toFile())) {
					GSON.toJson(jsonObject, writer);
				}

			} catch (IOException e) {
				LOGGER.error("Error updating info: ", e);
			}
		}
	}

	public ArrayList<Script> listScriptsFromFolder(Path folder){
//		ArrayList<Script> files = new ArrayList<>();
//		for (final File file : folder.listFiles()) {
//			files.add(new Script(file.getPath()));
//		}
//		return files;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
			ArrayList<Script> scripts = new ArrayList<>();
			for (Path file : stream) {
				scripts.add(new Script(file.toFile().getName())); // stupid, I know.
			}
			return scripts;
		} catch (IOException e){
			LOGGER.error("Could not list files: ", e);
			return new ArrayList<>();
		}
	}

	public class ExecuteCode extends Thread {
		@Override
		public void run(){
			// here do the code decoding thing :)
			// actually do it in another file
			// you can figure it out
		}
	}
}
// Note to self:
// Use client for the menu and the actually script decoding, and main to do the actions and commands. (right?)
// we got this :3c
// :3c
// :3c
// ;3c