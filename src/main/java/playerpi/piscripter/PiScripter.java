package playerpi.piscripter;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PiScripter implements ModInitializer {
	public static final String MOD_ID = "piscripter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("piScripter mod initializing...");

		PayloadTypeRegistry.serverboundPlay().register(ServerboundShowResultMessagePayload.TYPE, ServerboundShowResultMessagePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ServerboundShowResultMessagePayload.TYPE, (payload, context) -> {
			if (payload.message == "") {
				context.player().sendSystemMessage(Component.literal("Something may have gone wrong and-or result is empty."));
			}
			context.player().sendSystemMessage(Component.literal(payload.message));
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
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
