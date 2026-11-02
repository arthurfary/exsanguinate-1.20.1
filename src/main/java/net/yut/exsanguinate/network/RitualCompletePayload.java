package net.yut.exsanguinate.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.yut.exsanguinate.Exsanguinate;

/**
 * Server -> client: "a blood ritual just completed at this altar". Sent only
 * to the players standing close; the client gives the camera one dull thud,
 * softer the further away it is (see ExsanguinateClient).
 */
public record RitualCompletePayload(BlockPos pos) implements FabricPacket {

    public static final PacketType<RitualCompletePayload> TYPE = PacketType.create(
            new Identifier(Exsanguinate.MOD_ID, "ritual_complete"),
            buf -> new RitualCompletePayload(buf.readBlockPos())
    );

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
