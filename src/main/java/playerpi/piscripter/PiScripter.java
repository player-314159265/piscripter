package playerpi.piscripter;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.logging.log4j.core.tools.picocli.CommandLine.run;

public class PiScripter implements ModInitializer {
	public static final String MOD_ID = "piscripter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("piScripter mod initializing...");

		PayloadTypeRegistry.serverboundPlay().register(ServerboundShowResultMessagePayload.TYPE, ServerboundShowResultMessagePayload.CODEC);

		PayloadTypeRegistry.clientboundPlay().register(ClientboundRunCommandPayload.TYPE, ClientboundRunCommandPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundNewFilePayload.TYPE, ClientboundNewFilePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundReadFilePayload.TYPE, ClientboundReadFilePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundGetInfoPayload.TYPE, ClientboundGetInfoPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundGetScriptsPayload.TYPE, ClientboundGetScriptsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundSetScriptLinePayload.TYPE, ClientboundSetScriptLinePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundListBreakpointsPayload.TYPE, ClientboundListBreakpointsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundDeleteFilePayload.TYPE, ClientboundDeleteFilePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundOpenFolderPayload.TYPE, ClientboundOpenFolderPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundScriptInsertLinePayload.TYPE, ClientboundScriptInsertLinePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundScriptAddLinePayload.TYPE, ClientboundScriptAddLinePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundScriptRemoveLinesPayload.TYPE, ClientboundScriptRemoveLinesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundScriptDuplicateFilePayload.TYPE, ClientboundScriptDuplicateFilePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundScriptRenameFilePayload.TYPE, ClientboundScriptRenameFilePayload.CODEC);


		ServerPlayNetworking.registerGlobalReceiver(ServerboundShowResultMessagePayload.TYPE, (payload, context) -> {
			if (payload.message == "") {
				context.player().sendSystemMessage(Component.literal("Something may have gone wrong and-or result is empty."));
			}
			context.player().sendSystemMessage(Component.literal(payload.message));
		});

		// COMMANDS
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("piscript").executes(PiScripter::executePiScript) //opens menu if no arguments
					.then(Commands.literal("console").executes(PiScripter::executeConsole) //opens console (inside menu if no arguments)
						.then(Commands.literal("from") // from
							.then(Commands.argument("script", StringArgumentType.string()) //from what script
								.then(Commands.argument("Expression", StringArgumentType.string() ).executes(PiScripter::executeConsoleRunScript) ))) // what expression
						.then(Commands.literal("raw") //runs an expression directly without depending on a scripts
							.then(Commands.argument("Expression", StringArgumentType.string() ).executes(PiScripter::executeConsoleRun))))
					.then(Commands.literal("scripts").executes(PiScripter::executeScriptsList) //edit scripts from the chat directly (opens the scripts menu if no arguments)
						.then(Commands.argument("script", StringArgumentType.string()).executes(PiScripter::executeOpenScriptMenu) //opens the script's menu if no args given
							.then(Commands.literal("info").executes(PiScripter::executeScriptInfo)) //shows info about the script (length, dependencies, author, etc.)
							.then(Commands.literal("get").executes(PiScripter::executeShowFullScript) //shows the whole script
								.then(Commands.literal("all").executes(PiScripter::executeShowFullScript)) //optional. does the same thing (shows the whole script)
									.then(Commands.literal("line")
										.then(Commands.argument("Index", IntegerArgumentType.integer(1)).executes(PiScripter::executeShowScriptLine))) //index
								.then(Commands.literal("lines") //shows lines from a specified area of the code (minimum to maximum)
									.then(Commands.argument("minimum", IntegerArgumentType.integer(1)).executes(PiScripter::executeShowScriptLinesFromMinimum) //shows lines from a minimum line index
										.then(Commands.argument("maximum", IntegerArgumentType.integer(1)).executes(PiScripter::executeShowScriptLinesFromMinimumToMaximum))))) //to a maximum line index
							.then(Commands.literal("edit") // edit the file
								.then(Commands.literal("setline") // sets the line
									.then(Commands.argument("Index", IntegerArgumentType.integer(1)) // line index
										.then(Commands.argument("Expression", StringArgumentType.string()).executes(PiScripter::executeScriptSetLineAtIndex)))) // to a new expression
								.then(Commands.literal("addline")
									.then(Commands.argument("Expression", StringArgumentType.string()).executes(PiScripter::executeScriptAddLine)))
								.then(Commands.literal("insertline")
									.then(Commands.argument("Index", IntegerArgumentType.integer(1))
										.then(Commands.argument("Expression", StringArgumentType.string()).executes(PiScripter::executeScriptInsertLine))))
								.then(Commands.literal("removeline")
									.then(Commands.argument("Index", IntegerArgumentType.integer()).executes(PiScripter::executeScriptRemoveLine)))
								.then(Commands.literal("removelines")
									.then(Commands.argument("IndexMin", IntegerArgumentType.integer(1))
										.then(Commands.argument("IndexMax", IntegerArgumentType.integer()).executes(PiScripter::executeScriptRemoveLines)))))
							.then(Commands.literal("run").executes(PiScripter::executeScriptExecuteNormal) // runs script
								.then(Commands.literal("normal").executes(PiScripter::executeScriptExecuteNormal)) // runs script in normal mode (default)
								.then(Commands.literal("debug").executes(PiScripter::executeScriptExecuteDebug))) // runs script in debug mode
							.then(Commands.literal("breakpoint") // breakpoint options
								.then(Commands.literal("list").executes(PiScripter::executeBreakpointsList)) // lists breakpoints in the script
								.then(Commands.literal("toggleat") // toggles on or off a breakpoint in the script at line:
									.then(Commands.argument("Index", IntegerArgumentType.integer(1)).executes(PiScripter::executeToggleBreakpointAtIndex))) // line index
								.then(Commands.literal("removeall").executes(PiScripter::executeRemoveAllBreakpoints))) // removes all breakpoints
							.then(Commands.literal("delete").executes(PiScripter::executeDeleteFileWarn)  // warn about deleting the file
								.then(Commands.argument("scriptAgain", StringArgumentType.string()).executes(PiScripter::executeDeleteFile)))
							.then(Commands.literal("folder").executes(PiScripter::executeOpenFileFolder)) // open folder
							.then(Commands.literal("duplicate")
								.then(Commands.argument("name", StringArgumentType.string()).executes(PiScripter::executeDuplicateFile)))
							.then(Commands.literal("rename")
								.then(Commands.argument("name", StringArgumentType.string()).executes(PiScripter::executeRenameFile)))))
					.then(Commands.literal("create") // creates a script of name:
						.then(Commands.argument("scriptName", StringArgumentType.string()).executes(PiScripter::executeCreateNewScript))) // script name
					.then(Commands.literal("folder").executes(PiScripter::executeOpenFolder) // open main folder, or if given argument, open folder of:
						.then(Commands.argument("script", StringArgumentType.string()).executes(PiScripter::executeOpenFileFolder)))); // script
		});

		LOGGER.info("piScripter mod initialized!");
	}

	public record ServerboundShowResultMessagePayload(String message) implements CustomPacketPayload {
		public static final Identifier SHOW_RESULT_MESSAGE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "show_file");
		public static final CustomPacketPayload.Type<ServerboundShowResultMessagePayload> TYPE = new CustomPacketPayload.Type<>(SHOW_RESULT_MESSAGE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundShowResultMessagePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ServerboundShowResultMessagePayload::message, ServerboundShowResultMessagePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}



	public record ClientboundRunCommandPayload(String command) implements CustomPacketPayload {
		public static final Identifier RUN_COMMAND_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "run_command");
		public static final CustomPacketPayload.Type<ClientboundRunCommandPayload> TYPE = new CustomPacketPayload.Type<>(RUN_COMMAND_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRunCommandPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundRunCommandPayload::command, ClientboundRunCommandPayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundNewFilePayload(String fileName) implements CustomPacketPayload {
		public static final Identifier CREATE_FILE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "create_file");
		public static final CustomPacketPayload.Type<ClientboundNewFilePayload> TYPE = new CustomPacketPayload.Type<>(CREATE_FILE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundNewFilePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundNewFilePayload::fileName, ClientboundNewFilePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundReadFilePayload(String fileNameAndLineIndexes) implements CustomPacketPayload {
		public static final Identifier READ_FILE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "read_file");
		public static final CustomPacketPayload.Type<ClientboundReadFilePayload> TYPE = new CustomPacketPayload.Type<>(READ_FILE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundReadFilePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundReadFilePayload::fileNameAndLineIndexes, ClientboundReadFilePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundGetInfoPayload(String fileName) implements CustomPacketPayload {
		public static final Identifier GET_INFO_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "get_info");
		public static final CustomPacketPayload.Type<ClientboundGetInfoPayload> TYPE = new CustomPacketPayload.Type<>(GET_INFO_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundGetInfoPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundGetInfoPayload::fileName, ClientboundGetInfoPayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundGetScriptsPayload(boolean sayInChat) implements CustomPacketPayload {
		public static final Identifier GET_SCRIPTS_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "get_scripts");
		public static final CustomPacketPayload.Type<ClientboundGetScriptsPayload> TYPE = new CustomPacketPayload.Type<>(GET_SCRIPTS_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundGetScriptsPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ClientboundGetScriptsPayload::sayInChat, ClientboundGetScriptsPayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundSetScriptLinePayload(String fileNameLineAndExpression) implements CustomPacketPayload {
		public static final Identifier SET_SCRIPT_LINE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "set_script_line");
		public static final CustomPacketPayload.Type<ClientboundSetScriptLinePayload> TYPE = new CustomPacketPayload.Type<>(SET_SCRIPT_LINE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetScriptLinePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundSetScriptLinePayload::fileNameLineAndExpression, ClientboundSetScriptLinePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundListBreakpointsPayload(String fileName) implements CustomPacketPayload {
		public static final Identifier LIST_BREAKPOINTS_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "list_breakpoints");
		public static final CustomPacketPayload.Type<ClientboundListBreakpointsPayload> TYPE = new CustomPacketPayload.Type<>(LIST_BREAKPOINTS_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundListBreakpointsPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundListBreakpointsPayload::fileName, ClientboundListBreakpointsPayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundDeleteFilePayload(String fileName) implements CustomPacketPayload {
		public static final Identifier DELETE_FILE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "delete_file");
		public static final CustomPacketPayload.Type<ClientboundDeleteFilePayload> TYPE = new CustomPacketPayload.Type<>(DELETE_FILE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDeleteFilePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundDeleteFilePayload::fileName, ClientboundDeleteFilePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundOpenFolderPayload(String script) implements CustomPacketPayload { // open script folder. If script == "" it'll open the main script folder
		public static final Identifier OPEN_FOLDER_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "open_folder");
		public static final CustomPacketPayload.Type<ClientboundOpenFolderPayload> TYPE = new CustomPacketPayload.Type<>(OPEN_FOLDER_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenFolderPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundOpenFolderPayload::script, ClientboundOpenFolderPayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundScriptInsertLinePayload(String fileNameLineAndExpression) implements CustomPacketPayload {
		public static final Identifier INSERT_LINE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "insert_line");
		public static final CustomPacketPayload.Type<ClientboundScriptInsertLinePayload> TYPE = new CustomPacketPayload.Type<>(INSERT_LINE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScriptInsertLinePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundScriptInsertLinePayload::fileNameLineAndExpression, ClientboundScriptInsertLinePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundScriptAddLinePayload(String fileNameAndExpression) implements CustomPacketPayload {
		public static final Identifier ADD_LINE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "add_line");
		public static final CustomPacketPayload.Type<ClientboundScriptAddLinePayload> TYPE = new CustomPacketPayload.Type<>(ADD_LINE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScriptAddLinePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundScriptAddLinePayload::fileNameAndExpression, ClientboundScriptAddLinePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundScriptRemoveLinesPayload(String fileNameAndLines) implements CustomPacketPayload {
		public static final Identifier REMOVE_LINES_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "remove_lines");
		public static final CustomPacketPayload.Type<ClientboundScriptRemoveLinesPayload> TYPE = new CustomPacketPayload.Type<>(REMOVE_LINES_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScriptRemoveLinesPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundScriptRemoveLinesPayload::fileNameAndLines, ClientboundScriptRemoveLinesPayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundScriptDuplicateFilePayload(String fileNameAndNewName) implements CustomPacketPayload {
		public static final Identifier DUPLICATE_FILE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "duplicate_file");
		public static final CustomPacketPayload.Type<ClientboundScriptDuplicateFilePayload> TYPE = new CustomPacketPayload.Type<>(DUPLICATE_FILE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScriptDuplicateFilePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundScriptDuplicateFilePayload::fileNameAndNewName, ClientboundScriptDuplicateFilePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	public record ClientboundScriptRenameFilePayload(String fileNameAndNewName) implements CustomPacketPayload {
		public static final Identifier RENAME_FILE_PAYLOAD_ID = Identifier.fromNamespaceAndPath(PiScripter.MOD_ID, "rename_file");
		public static final CustomPacketPayload.Type<ClientboundScriptRenameFilePayload> TYPE = new CustomPacketPayload.Type<>(RENAME_FILE_PAYLOAD_ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScriptRenameFilePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientboundScriptRenameFilePayload::fileNameAndNewName, ClientboundScriptRenameFilePayload::new);
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}



	// Command actions for /piscript ...

	private static int executeConsole(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("\nOpening Console Menu"), false);
		return 1;
	}
	private static int executeConsoleRun(CommandContext<CommandSourceStack> context) {
		String argExpression = StringArgumentType.getString(context, "Expression");
		context.getSource().sendSuccess(() -> Component.literal("\nRunning expression " + argExpression), false);
		return 1;
	}
	private static int executeConsoleRunScript(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		String argExpression = StringArgumentType.getString(context, "Expression");
		context.getSource().sendSuccess(() -> Component.literal("\nRunning expression " + argExpression + " inside console of script " + argScript), false);
		return 1;
	}
	private static int executePiScript(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("\nOpening piScript Main Menu"), false);
		return 1;
	}
	private static int executeScriptInfo(CommandContext<CommandSourceStack> context) { // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nFetching info from script " + argScript), false);

		ClientboundGetInfoPayload payload = new ClientboundGetInfoPayload(argScript);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeOpenScriptMenu(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nOpening menu of script " + argScript), false);
		return 1;
	}
	private static int executeShowFullScript(CommandContext<CommandSourceStack> context) { // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nShowing the full script of " + argScript + "\n"), false);

		ClientboundReadFilePayload payload = new ClientboundReadFilePayload(argScript + "|1|-1");
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
		LOGGER.debug("showing full script");

		return 1;
	}
	private static int executeShowScriptLine(CommandContext<CommandSourceStack> context) {  // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		int argLine = IntegerArgumentType.getInteger(context, "Index");
		context.getSource().sendSuccess(() -> Component.literal("\nShowing line " + argLine + " of " + argScript + "\n"), false);

		ClientboundReadFilePayload payload = new ClientboundReadFilePayload(argScript + "|" + argLine + "|" + argLine);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeShowScriptLinesFromMinimum(CommandContext<CommandSourceStack> context) {  // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		int argLineMin = IntegerArgumentType.getInteger(context, "minimum");
		context.getSource().sendSuccess(() -> Component.literal("\nShowing all lines from " + argLineMin + " of " + argScript + "\n"), false);

		ClientboundReadFilePayload payload = new ClientboundReadFilePayload(argScript + "|" + argLineMin + "|-1");
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeShowScriptLinesFromMinimumToMaximum(CommandContext<CommandSourceStack> context) {  // done functionally
		String argScript = StringArgumentType.getString(context, "script");
		int argLineMin = IntegerArgumentType.getInteger(context, "minimum");
		int argLineMax = IntegerArgumentType.getInteger(context, "maximum");
		if (argLineMax < argLineMin) {
			context.getSource().sendFailure(Component.literal("ERROR: Line index maximum cannot be greater than line index minimum!"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("\nShowing lines " + argLineMin + " to " + argLineMax + " of " + argScript + "\n"), false);

		ClientboundReadFilePayload payload = new ClientboundReadFilePayload(argScript + "|" + argLineMin + "|" + argLineMax);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeScriptsList(CommandContext<CommandSourceStack> context) { // done functionally
		context.getSource().sendSuccess(() -> Component.literal("\nShowing all scripts"), false);

		ClientboundGetScriptsPayload payload = new ClientboundGetScriptsPayload(true);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeScriptSetLineAtIndex(CommandContext<CommandSourceStack> context) {
		int argLine = IntegerArgumentType.getInteger(context, "Index");
		String argScript = StringArgumentType.getString(context, "script");
		String argExpression = StringArgumentType.getString(context, "Expression");
		context.getSource().sendSuccess(() -> Component.literal("\nSetting line " + argLine + " to " + argExpression), false);

		ClientboundSetScriptLinePayload payload = new ClientboundSetScriptLinePayload(argScript + "|" + argLine + "|" + argExpression);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeScriptExecuteNormal(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nExecuting script " + argScript), false);
		return 1;
	}
	private static int executeScriptExecuteDebug(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nExecuting script " + argScript + " in debug mode"), false);
		return 1;
	}
	private static int executeBreakpointsList(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nListing all breakpoints of script " + argScript), false);

		ClientboundListBreakpointsPayload payload = new ClientboundListBreakpointsPayload(argScript);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeRemoveAllBreakpoints(CommandContext<CommandSourceStack> context) {
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nRemoving all breakpoints of script " + argScript), false);
		return 1;
	}
	private static int executeToggleBreakpointAtIndex(CommandContext<CommandSourceStack> context) {
		int argLine = IntegerArgumentType.getInteger(context, "Index");
		String argScript = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nToggling breakpoint of script " + argScript + " at line " + argLine), false);
		return 1;
	}
	private static int executeCreateNewScript(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "scriptName");

		if (argScriptName.contains("|")) {
			context.getSource().sendFailure(Component.literal("You may not use this character in your script's name: |"));
			return 0;
		} else {
			context.getSource().sendSuccess(() -> Component.literal("\nCreating new script named " + argScriptName), false);

			ClientboundNewFilePayload payload = new ClientboundNewFilePayload(argScriptName);
			ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

			return 1;
		}
	}
	private static int executeDeleteFileWarn(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		context.getSource().sendSuccess(() -> Component.literal("\nPlease execute \"/piscript scripts " + argScriptName + " delete " + argScriptName + "\" to actually delete it. This is to avoid accidental deletions as this cannot be undone."), false);
		return 1;
	}
	private static int executeDeleteFile(CommandContext<CommandSourceStack> context) { // done functionally
		String argScriptName = StringArgumentType.getString(context, "script");
		String argScriptConfirm = StringArgumentType.getString(context, "scriptAgain");
		if (argScriptConfirm.equals(argScriptName)) {
			context.getSource().sendSuccess(() -> Component.literal("\nDeleting file " + argScriptName + "."), false);

			ClientboundDeleteFilePayload payload = new ClientboundDeleteFilePayload(argScriptName);
			ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
		} else {
			context.getSource().sendFailure(Component.literal("Please execute \"/piscript scripts " + argScriptName + " delete " + argScriptName + "\" to actually delete it. This is to avoid accidental deletions as this cannot be undone."));
		}
		return 1;
	}
	private static int executeOpenFileFolder(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");

		ClientboundOpenFolderPayload payload = new ClientboundOpenFolderPayload(argScriptName);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);

		return 1;
	}
	private static int executeOpenFolder(CommandContext<CommandSourceStack> context) {
		ClientboundOpenFolderPayload payload = new ClientboundOpenFolderPayload("");
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
		return 1;
	}
	private static int executeScriptAddLine(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");
		String argExpression = StringArgumentType.getString(context, "Expression");

		context.getSource().sendSuccess(() -> Component.literal("\nAdd line \"" + argExpression + "\" to script " + argScriptName + "."), false);

		ClientboundScriptAddLinePayload payload = new ClientboundScriptAddLinePayload(argScriptName + "|" + argExpression);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
		return 1;
	}
	private static int executeScriptInsertLine(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");
		int argLine = IntegerArgumentType.getInteger(context, "Index");
		String argExpression = StringArgumentType.getString(context, "Expression");

		context.getSource().sendSuccess(() -> Component.literal("\nInserting line \"" + argExpression + "\" to script " + argScriptName + " at line " + argLine + "."), false);

		ClientboundScriptInsertLinePayload payload = new ClientboundScriptInsertLinePayload(argScriptName + "|" + argLine + "|" + argExpression);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
		return 1;
	}
	private static int executeScriptRemoveLine(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");
		int argLine = IntegerArgumentType.getInteger(context, "Index");

		context.getSource().sendSuccess(() -> Component.literal("\nRemoving line " + argLine + " of script " + argScriptName + "."), false);

		ClientboundScriptRemoveLinesPayload payload = new ClientboundScriptRemoveLinesPayload(argScriptName + "|" + argLine + "|" + argLine);
		ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
		return 1;
	}
	private static int executeScriptRemoveLines(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");
		int argLineMin = IntegerArgumentType.getInteger(context, "IndexMin");
		int argLineMax = IntegerArgumentType.getInteger(context, "IndexMax");

		if ( argLineMax < argLineMin ) {
			context.getSource().sendFailure(Component.literal("Minimum line may not be greater than maximum line."));
			return 0;
		} else {
			context.getSource().sendSuccess(() -> Component.literal("\nRemoving lines " + argLineMin + " to " + argLineMax + " of script " + argScriptName + "."), false);

			ClientboundScriptRemoveLinesPayload payload = new ClientboundScriptRemoveLinesPayload(argScriptName + "|" + argLineMin + "|" + argLineMax);
			ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
			return 1;
		}
	}
	private static int executeDuplicateFile(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");
		String argNewName = StringArgumentType.getString(context, "name");

		if ( argNewName.contains("|") ) {
			context.getSource().sendFailure(Component.literal("You may not use this character in your script's name: |"));
			return 0;
		} else {
			context.getSource().sendSuccess(() -> Component.literal("\nDuplicating file " + argScriptName + " to " + argNewName), false);

			ClientboundScriptDuplicateFilePayload payload = new ClientboundScriptDuplicateFilePayload(argScriptName + "|" + argNewName);
			ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
			return 1;
		}
	}
	private static int executeRenameFile(CommandContext<CommandSourceStack> context) {
		String argScriptName = StringArgumentType.getString(context, "script");
		String argNewName = StringArgumentType.getString(context, "name");

		if ( argNewName.contains("|") ) {
			context.getSource().sendFailure(Component.literal("You may not use this character in your script's name: |"));
			return 0;
		} else {
			context.getSource().sendSuccess(() -> Component.literal("\nRenaming file " + argScriptName + " to " + argNewName), false);

			ClientboundScriptRenameFilePayload payload = new ClientboundScriptRenameFilePayload(argScriptName + "|" + argNewName);
			ServerPlayNetworking.send(context.getSource().getPlayer(), payload);
			return 1;
		}
	}


	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
