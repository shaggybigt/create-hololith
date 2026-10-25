package dev.hololith.client;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
final class HololithRenderTypes {
    static final RenderType TERRAIN=RenderType.create("hololith_terrain",
        DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,false,
        RenderType.CompositeState.builder()
        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
        .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false))
        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
        .setCullState(RenderStateShard.NO_CULL).setOverlayState(RenderStateShard.OVERLAY)
        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
    static final RenderType LIGHT=RenderType.create("hololith_light",
        DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,VertexFormat.Mode.QUADS,1536,false,false,
        RenderType.CompositeState.builder()
        .setShaderState(RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SHADER)
        .setTextureState(RenderStateShard.NO_TEXTURE)
        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
        .setLightmapState(RenderStateShard.LIGHTMAP).setCullState(RenderStateShard.NO_CULL)
        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    // SRC_ALPHA, ONE keeps additive highlights subtle while preserving their opacity.
    static final RenderType GLOW=RenderType.create("hololith_glow",
        DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,VertexFormat.Mode.QUADS,1536,false,false,
        RenderType.CompositeState.builder()
        .setShaderState(RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SHADER)
        .setTextureState(RenderStateShard.NO_TEXTURE)
        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
        .setLightmapState(RenderStateShard.LIGHTMAP).setCullState(RenderStateShard.NO_CULL)
        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private HololithRenderTypes(){}
}
