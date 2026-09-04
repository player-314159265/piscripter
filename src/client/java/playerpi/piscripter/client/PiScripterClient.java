package playerpi.piscripter.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;

import static playerpi.piscripter.PiScripter.LOGGER;

public class PiScripterClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		Path scriptFolder = Script.MAIN_FOLDER;
		if (!Files.exists(scriptFolder)) {
            try {
                Files.createDirectory(scriptFolder);
				LOGGER.info("Main 'scripts' folder not found. Created the directory.");
            } catch (IOException e) {
                LOGGER.error("Could not create scripts directory: ", e);
            }
		}

		// COMMAND
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("piscript").executes(PiScripterClient::executePiScript) //opens menu if no arguments
					.then(Commands.literal("console").executes(PiScripterClient::executeConsole) //opens console (inside menu if no arguments)
							.then(Commands.literal("from") // from
									.then(Commands.argument("script", StringArgumentType.string()) //from what script
											.then(Commands.argument("expression", StringArgumentType.string() ).executes(PiScripterClient::executeConsoleRunScript) ))) // what expression
							.then(Commands.literal("raw") //runs an expression directly without depending on a scripts
									.then(Commands.argument("expression", StringArgumentType.string() ).executes(PiScripterClient::executeConsoleRun))))
					.then(Commands.literal("scripts").executes(PiScripterClient::executeScriptsList) //edit scripts from the chat directly (opens the scripts menu if no arguments)
							.then(Commands.argument("script", StringArgumentType.string()).executes(PiScripterClient::executeOpenScriptMenu) //opens the script's menu if no args given
									.then(Commands.literal("info").executes(PiScripterClient::executeScriptInfo)) //shows info about the script (length, dependencies, author, etc.)
									.then(Commands.literal("get").executes(PiScripterClient::executeShowFullScript) //shows the whole script
											.then(Commands.literal("all").executes(PiScripterClient::executeShowFullScript)) //optional. does the same thing (shows the whole script)
											.then(Commands.literal("line")
													.then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(PiScripterClient::executeShowScriptLine))) //index
											.then(Commands.literal("lines") //shows lines from a specified area of the code (minimum to maximum)
													.then(Commands.argument("minimum", IntegerArgumentType.integer(1)).executes(PiScripterClient::executeShowScriptLinesFromMinimum) //shows lines from a minimum line index
															.then(Commands.argument("maximum", IntegerArgumentType.integer(1)).executes(PiScripterClient::executeShowScriptLinesFromMinimumToMaximum))))) //to a maximum line index
									.then(Commands.literal("edit") // edit the file
											.then(Commands.literal("setline") // sets the line
													.then(Commands.argument("index", IntegerArgumentType.integer(1)) // line index
															.then(Commands.argument("expression", StringArgumentType.string()).executes(PiScripterClient::executeScriptSetLineAtIndex)))) // to a new expression
											.then(Commands.literal("addline")
													.then(Commands.argument("expression", StringArgumentType.string()).executes(PiScripterClient::executeScriptAddLine)))
											.then(Commands.literal("insertline")
													.then(Commands.argument("index", IntegerArgumentType.integer(1))
															.then(Commands.argument("expression", StringArgumentType.string()).executes(PiScripterClient::executeScriptInsertLine))))
											.then(Commands.literal("removeline")
													.then(Commands.argument("index", IntegerArgumentType.integer()).executes(PiScripterClient::executeScriptRemoveLine)))
											.then(Commands.literal("removelines")
													.then(Commands.argument("indexMin", IntegerArgumentType.integer(1))
															.then(Commands.argument("indexMax", IntegerArgumentType.integer()).executes(PiScripterClient::executeScriptRemoveLines)))))
									.then(Commands.literal("run").executes(PiScripterClient::executeScriptExecuteNormal) // runs script
											.then(Commands.literal("normal").executes(PiScripterClient::executeScriptExecuteNormal)) // runs script in normal mode (default)
											.then(Commands.literal("debug").executes(PiScripterClient::executeScriptExecuteDebug))) // runs script in debug mode
									.then(Commands.literal("breakpoint") // breakpoint options
											.then(Commands.literal("list").executes(PiScripterClient::executeBreakpointsList)) // lists breakpoints in the script
											.then(Commands.literal("toggleat") // toggles on or off a breakpoint in the script at line:
													.then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(PiScripterClient::executeToggleBreakpointAtIndex))) // line index
											.then(Commands.literal("removeall").executes(PiScripterClient::executeRemoveAllBreakpoints))) // removes all breakpoints
									.then(Commands.literal("delete").executes(PiScripterClient::executeDeleteFileWarn)  // warn about deleting the file
											.then(Commands.argument("scriptAgain", StringArgumentType.string()).executes(PiScripterClient::executeDeleteFile)))
									.then(Commands.literal("folder").executes(PiScripterClient::executeOpenFileFolder)) // open folder
									.then(Commands.literal("duplicate")
											.then(Commands.argument("name", StringArgumentType.string()).executes(PiScripterClient::executeDuplicateFile)))
									.then(Commands.literal("rename")
											.then(Commands.argument("name", StringArgumentType.string()).executes(PiScripterClient::executeRenameFile)))
									.then(Commands.literal("stop").executes(PiScripterClient::executeStopScript))))
					.then(Commands.literal("create") // creates a script of name:
							.then(Commands.argument("scriptName", StringArgumentType.string()).executes(PiScripterClient::executeCreateNewScript))) // script name
					.then(Commands.literal("folder").executes(PiScripterClient::executeOpenFolder) // open main folder, or if given argument, open folder of:
							.then(Commands.argument("script", StringArgumentType.string()).executes(PiScripterClient::executeOpenFileFolder)))); // script
		});


		//removed all the clientplay networking things.
	}

	public static void runCommand(String command) {
		Minecraft.getInstance().getConnection().sendCommand(command);
	}

	public static void sendChat(String message) { //this is the player sending the message
		Minecraft.getInstance().getConnection().sendChat(message);
	}

	public static void sendChatSystemMessage(String message) {
		if (message == "") {
			Minecraft.getInstance().player.sendSystemMessage(Component.literal("Something may have gone wrong and-or result is empty."));
		}
		Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
	}

	// Command actions for /piscript ...

	private static int executeConsole(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nOpening Console Menu"), false);
		return 1;
	}
	private static int executeConsoleRun(CommandContext<CommandSourceStack> context) {
		String argExpression = StringArgumentType.getString(context, "expression");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nRunning expression " + argExpression), false);
		return 1;
	}
	private static int executeConsoleRunScript(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		String argExpression = StringArgumentType.getString(context, "expression");
		Script script = new Script(argScript);
		if (!script.exists()) {
			context.getSource().sendFailure(Component.literal("Script " + argScript + " not found."));
			return 0;
		}
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nRunning expression " + argExpression + " inside console of script " + argScript), false);
		return 1;
	}
	private static int executePiScript(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nOpening piScript Main Menu"), false);
		Minecraft.getInstance().gui.setScreen(
				new PiScriptMenu(Component.empty())
		); // open menu
		return 1;
	}
	private static int executeScriptInfo(CommandContext<CommandSourceStack> context) { // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nFetching info from script " + argScript), false);

		Script script = new Script(argScript);

		if (Files.exists(script.getInfoFile())) {
			String infoContents =
					"Name: " + script.getInfoName()
							+ "\nDescription: " + script.getInfoDescription()
							+ "\nGame Version: " + script.getInfoGameVersion()
							+ "\nLanguage Version: " + script.getInfoLanguageVersion()
							+ "\nAuthors: " + script.getInfoAuthorsString()
							+ "\nDate of Creation: " + script.getInfoDateOfCreation();

			sendChatSystemMessage(infoContents);
		} else {
			sendChatSystemMessage(""); // Brings error
		}

		return 1;
	}
	private static int executeOpenScriptMenu(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nOpening menu of script " + argScript), false);
		return 1;
	}
	private static int executeShowFullScript(CommandContext<CommandSourceStack> context) { // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nShowing the full script of " + argScript + "\n"), false);

		Script script = new Script(argScript);

		String fileContents = script.readScriptFile(1, -1, true);
		sendChatSystemMessage(fileContents);
		return 1;
	}
	private static int executeShowScriptLine(CommandContext<CommandSourceStack> context) {  // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		int argLine = IntegerArgumentType.getInteger(context, "index");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nShowing line " + argLine + " of " + argScript + "\n"), false);

		Script script = new Script(argScript);

		String fileContents = script.readScriptFile(argLine, argLine, true);
		sendChatSystemMessage(fileContents);

		return 1;
	}
	private static int executeShowScriptLinesFromMinimum(CommandContext<CommandSourceStack> context) {  // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		int argLineMin = IntegerArgumentType.getInteger(context, "minimum");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nShowing all lines from " + argLineMin + " of " + argScript + "\n"), false);

		Script script = new Script(argScript);

		String fileContents = script.readScriptFile(argLineMin, -1, true);
		sendChatSystemMessage(fileContents);
		return 1;
	}
	private static int executeShowScriptLinesFromMinimumToMaximum(CommandContext<CommandSourceStack> context) {  // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		int argLineMin = IntegerArgumentType.getInteger(context, "minimum");
		int argLineMax = IntegerArgumentType.getInteger(context, "maximum");
		if (argLineMax < argLineMin) {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("ERROR: Line index maximum cannot be greater than line index minimum!"));
			return 0;
		}
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nShowing lines " + argLineMin + " to " + argLineMax + " of " + argScript + "\n"), false);

		Script script = new Script(argScript);

		String fileContents = script.readScriptFile(argLineMin, argLineMax, true);
		sendChatSystemMessage(fileContents);
		return 1;
	}
	private static int executeScriptsList(CommandContext<CommandSourceStack> context) { // done functionally
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nShowing all scripts"), false);

		Path folder = new Script("").MAIN_FOLDER;
		String scripts = "";
		for (Script script : listScriptsFromFolder(folder)) {
			scripts += script.fileName + ", ";
		}
		if (scripts.length() > 1) {
			sendChatSystemMessage(scripts.substring(0, scripts.length() - 2));
		} else {
			sendChatSystemMessage("");
		}

		return 1;
	}
	private static int executeScriptSetLineAtIndex(CommandContext<CommandSourceStack> context) {
		int argLine = IntegerArgumentType.getInteger(context, "index");
		String argScript = StringArgumentType.getString(context, "script");
		String argExpression = StringArgumentType.getString(context, "expression");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nSetting line " + argLine + " to " + argExpression), false);

		Script script = new Script(argScript);
		script.setLine(argLine, argExpression);

		return 1;
	}
	private static int executeScriptExecuteNormal(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nExecuting script " + argScript), false);
		return 1;
	}
	private static int executeScriptExecuteDebug(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nExecuting script " + argScript + " in debug mode"), false);
		return 1;
	}
	private static int executeBreakpointsList(CommandContext<CommandSourceStack> context) { // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nListing all breakpoints of script " + argScript), false);

		sendChatSystemMessage( new Script(argScript).getInfoBreakpointsString() );
		return 1;
	}
	private static int executeRemoveAllBreakpoints(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nRemoving all breakpoints of script " + argScript), false);

		Script script = new Script(argScript);
		int[] newBreakpointsList = {};
		script.setInfoFileValue("breakpoints", newBreakpointsList);

		return 1;
	}
	private static int executeToggleBreakpointAtIndex(CommandContext<CommandSourceStack> context) {
		int argLine = IntegerArgumentType.getInteger(context, "index");
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nToggling breakpoint of script " + argScript + " at line " + argLine), false);

		Script script = new Script(argScript);
		int[] breakpointList = script.getInfoBreakpoints();
		ArrayList<Integer> newBreakpointList = new ArrayList<>();

		for (int breakpoint : breakpointList) {
			newBreakpointList.add(breakpoint);
		}
		if (newBreakpointList.contains(argLine)) {
			newBreakpointList.remove(newBreakpointList.indexOf(argLine));
		} else {
			newBreakpointList.add(argLine);
		}
		Object[] newBreakpointArray = newBreakpointList.toArray();
		Arrays.sort(newBreakpointArray);
		script.setInfoFileValue("breakpoints", newBreakpointArray);

		return 1;
	}
	private static int executeCreateNewScript(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "scriptName");

		if (!argScriptName.matches("\\w+")) {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("You may only use letters, numbers, and underscores to name your script!"));
			return 0;
		}
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nCreating new script named " + argScriptName), false);

		Script script = new Script(argScriptName);
		script.createScriptFile();

		return 1;

	}
	private static int executeDeleteFileWarn(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nPlease execute \"/piscript scripts " + argScriptName + " delete " + argScriptName + "\" to actually delete it. This is to avoid accidental deletions as this cannot be undone."), false);
		return 1;
	}
	private static int executeDeleteFile(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		String argScriptConfirm = StringArgumentType.getString(context, "scriptAgain");
		if (argScriptConfirm.equals(argScriptName)) {
			context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nDeleting file " + argScriptName + "."), false);

			Script script = new Script(argScriptName);
			script.delete();

		} else {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Please execute \"/piscript scripts " + argScriptName + " delete " + argScriptName + "\" to actually delete it. This is to avoid accidental deletions as this cannot be undone."));
		}
		return 1;
	}
	private static int executeOpenFileFolder(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");

		Script script = new Script(argScriptName);
		script.openFolder();

		return 1;
	}
	private static int executeOpenFolder(CommandContext<CommandSourceStack> context) { // done functionally
		Script.openMainFolder();
		return 1;
	}
	private static int executeScriptAddLine(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		String argExpression = StringArgumentType.getString(context, "expression");

		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nAdd line \"" + argExpression + "\" to script " + argScriptName + "."), false);

		Script script = new Script(argScriptName);
		script.addLine(argExpression);

		return 1;
	}
	private static int executeScriptInsertLine(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		int argLine = IntegerArgumentType.getInteger(context, "index");
		String argExpression = StringArgumentType.getString(context, "expression");

		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nInserting line \"" + argExpression + "\" to script " + argScriptName + " at line " + argLine + "."), false);

		Script script = new Script(argScriptName);
		script.insertLine(argLine,argExpression);

		return 1;
	}
	private static int executeScriptRemoveLine(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		int argLine = IntegerArgumentType.getInteger(context, "index");

		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nRemoving line " + argLine + " of script " + argScriptName + "."), false);

		Script script = new Script(argScriptName);
		script.removeLines(argLine,argLine);

		return 1;
	}
	private static int executeScriptRemoveLines(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		int argLineMin = IntegerArgumentType.getInteger(context, "indexMin");
		int argLineMax = IntegerArgumentType.getInteger(context, "indexMax");

		if ( argLineMax < argLineMin ) {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Minimum line may not be greater than maximum line."));
			return 0;
		} else {
			context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nRemoving lines " + argLineMin + " to " + argLineMax + " of script " + argScriptName + "."), false);

			Script script = new Script(argScriptName);
			script.removeLines(argLineMin,argLineMax);

			return 1;
		}
	}
	private static int executeDuplicateFile(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		String argNewName = StringArgumentType.getString(context, "name");

		if (!argNewName.matches("\\w+")) {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("You may only use letters, numbers, and underscores to name your script!"));
			return 0;
		}
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nDuplicating file " + argScriptName + " to " + argNewName), false);

		Script originalScript = new Script(argScriptName);
		Script newScript = new Script(argNewName);

		if (newScript.exists()) {
			sendChatSystemMessage("File " + argNewName + " already exists. Try using another name or deleting that one first.");
			return 0;
		} else {
			newScript.createScriptFile();
			newScript.setInfoFileValue("description", "Copy of " + argScriptName);
			newScript.setInfoFileValue("authors", originalScript.getInfoAuthors());

			try {
				Files.copy(originalScript.getScriptFile(), newScript.getScriptFile(), StandardCopyOption.REPLACE_EXISTING);
				Paths.get(newScript.getPath() + "\\" + argScriptName).toFile().renameTo(new File(newScript.MAIN_PATH + "\\" + argNewName + "\\" + argNewName));
			} catch (IOException e) {
				LOGGER.error("Could not copy script file: ", e);
			}
		}

		return 1;

	}
	private static int executeRenameFile(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		String argNewName = StringArgumentType.getString(context, "name");

		if (!argNewName.matches("\\w+")) {
			context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("You may only use letters, numbers, and underscores to name your script!"));
			return 0;
		}
		context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\nRenaming file " + argScriptName + " to " + argNewName), false);

		Script script = new Script(argScriptName);
		Script scriptNewName = new Script(argNewName);

		if (scriptNewName.exists()) {
			sendChatSystemMessage("File " + argNewName + " already exists. Try using another name or deleting that one first.");
		} else {
			script.setInfoFileValue("name", argNewName);
			script.getScriptFile().toFile().renameTo(new File(script.MAIN_PATH + "\\" + argScriptName + "\\" + argNewName + ".txt"));
			script.getScriptFolder().toFile().renameTo(new File(script.MAIN_PATH + "\\" + argNewName));
		}

		return 1;

	}
	private static int executeStopScript(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");

		context.getSource().sendSuccess(() -> Component.literal("Stopping script " + argScriptName), false);
		//context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Stopping script " + argScriptName), false);

		Script script = new Script(argScriptName);

		return 1;
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

	public static ArrayList<Script> listScriptsFromFolder(Path folder) { // maybe making this static is a bad idea. who knows
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
}
// Note to self:
// Use client for the menu and the actually script decoding, and main to do the actions and commands. (right?)
// we got this :3c
// :3c
// :3c
// ;3c
// :3c
// :3c
// :3c