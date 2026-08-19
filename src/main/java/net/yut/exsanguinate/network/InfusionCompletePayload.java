package net.yut.exsanguinate.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

/**
 * A tiny server -> client packet with no data at all. The server sends it
 * when the infusion completes; the client reacts by starting the 1 second
 * black & white flash (ColorFilter).
 *
 * This is needed because the infusion finishes on the SERVER (finishUsing),
 * but the post-processing shader only exists on the CLIENT.
 */
public record InfusionCompletePayload() implements FabricPacket {

    public static final PacketType<InfusionCompletePayload> TYPE = PacketType.create(
            new Identifier(Exsanguinate.MOD_ID, "infusion_complete"),
            buf -> new InfusionCompletePayload()
    );

    @Override
    public void write(PacketByteBuf buf) {
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}