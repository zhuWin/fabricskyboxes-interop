package me.flashyreese.mods.fabricskyboxes_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.fabricskyboxes.api.skyboxes.Skybox;
import io.github.amerebagatelle.fabricskyboxes.mixin.skybox.WorldRendererAccess;
import me.flashyreese.mods.fabricskyboxes_interop.client.config.FSBInteropConfig;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.List;

public class OptiFineCustomSky implements Skybox {
    public static final Codec<OptiFineCustomSky> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OptiFineSkyLayer.CODEC.listOf().optionalFieldOf("layers", ImmutableList.of()).forGetter(OptiFineCustomSky::getLayers),
            World.CODEC.fieldOf("world").forGetter(OptiFineCustomSky::getWorldIdentifier)
    ).apply(instance, OptiFineCustomSky::new));

    private final List<OptiFineSkyLayer> layers;
    private final RegistryKey<World> worldIdentifier;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private ClientWorld world = client.world;

    private boolean active = true;

    public OptiFineCustomSky(List<OptiFineSkyLayer> layers, RegistryKey<World> worldIdentifier) {
        this.layers = layers;
        this.worldIdentifier = worldIdentifier;
    }

    @Override
    public void render(WorldRendererAccess worldRendererAccess, MatrixStack matrixStack, Matrix4f matrix4f, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        this.world = (ClientWorld) camera.getFocusedEntity().getEntityWorld();
        this.renderSky(worldRendererAccess, matrixStack, matrix4f, tickDelta, camera, thickFog, fogCallback);
    }

    private void renderEndSky(MatrixStack matrices) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, WorldRendererAccess.getEndSky());
        for (int i = 0; i < 6; ++i) {
            matrices.push();
            if (i == 1) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
            }
            if (i == 2) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
            }
            if (i == 3) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
            }
            if (i == 4) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f));
            }
            if (i == 5) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0f));
            }
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            bufferBuilder.vertex(matrix4f, -100.0f, -100.0f, -100.0f).texture(0.0f, 0.0f).color(40, 40, 40, 255);
            bufferBuilder.vertex(matrix4f, -100.0f, -100.0f, 100.0f).texture(0.0f, 16.0f).color(40, 40, 40, 255);
            bufferBuilder.vertex(matrix4f, 100.0f, -100.0f, 100.0f).texture(16.0f, 16.0f).color(40, 40, 40, 255);
            bufferBuilder.vertex(matrix4f, 100.0f, -100.0f, -100.0f).texture(16.0f, 0.0f).color(40, 40, 40, 255);
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
            matrices.pop();
        }
        this.render(matrices, this.world, 0.0f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(WorldRendererAccess worldRendererAccess, MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        if (!thickFog) {
            CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
            if (cameraSubmersionType != CameraSubmersionType.POWDER_SNOW && cameraSubmersionType != CameraSubmersionType.LAVA && !this.hasBlindnessOrDarkness(camera)) {
                if (this.client.world.getDimensionEffects().getSkyType() == DimensionEffects.SkyType.END) {
                    this.renderEndSky(matrices);
                } else if (this.client.world.getDimensionEffects().getSkyType() == DimensionEffects.SkyType.NORMAL) {
                    Vec3d vec3d = this.world.getSkyColor(this.client.gameRenderer.getCamera().getPos(), tickDelta);
                    float f = (float)vec3d.x;
                    float g = (float)vec3d.y;
                    float h = (float)vec3d.z;
                    BackgroundRenderer.applyFogColor();
                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderColor(f, g, h, 1.0F);
                    ShaderProgram shaderProgram = RenderSystem.getShader();
                    worldRendererAccess.getLightSkyBuffer().bind();
                    worldRendererAccess.getLightSkyBuffer().draw(matrices.peek().getPositionMatrix(), projectionMatrix, shaderProgram);
                    VertexBuffer.unbind();
                    RenderSystem.enableBlend();
                    float[] fs = this.world.getDimensionEffects().getFogColorOverride(this.world.getSkyAngle(tickDelta), tickDelta);
                    if (fs != null) {
                        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        matrices.push();
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
                        float i = MathHelper.sin(this.world.getSkyAngleRadians(tickDelta)) < 0.0F ? 180.0F : 0.0F;
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
                        float j = fs[0];
                        float k = fs[1];
                        float l = fs[2];
                        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
                        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
                        bufferBuilder.vertex(matrix4f, 0.0F, 100.0F, 0.0F).color(j, k, l, fs[3]);
                        int m = 16;

                        for(int n = 0; n <= 16; ++n) {
                            float o = (float)n * (float) (Math.PI * 2) / 16.0F;
                            float p = MathHelper.sin(o);
                            float q = MathHelper.cos(o);
                            bufferBuilder.vertex(matrix4f, p * 120.0F, q * 120.0F, -q * 40.0F * fs[3]).color(fs[0], fs[1], fs[2], 0.0F);
                        }

                        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
                        matrices.pop();
                    }

                    RenderSystem.blendFuncSeparate(
                            GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO
                    );
                    matrices.push();
                    float i = 1.0F - this.world.getRainGradient(tickDelta);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, i);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
                    this.render(matrices, this.world, tickDelta);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.world.getSkyAngle(tickDelta) * 360.0F));
                    Matrix4f matrix4f2 = matrices.peek().getPositionMatrix();
                    float k = 30.0F;
                    RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                    RenderSystem.setShaderTexture(0, WorldRendererAccess.getSun());
                    BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    bufferBuilder.vertex(matrix4f2, -k, 100.0F, -k).texture(0.0F, 0.0F);
                    bufferBuilder.vertex(matrix4f2, k, 100.0F, -k).texture(1.0F, 0.0F);
                    bufferBuilder.vertex(matrix4f2, k, 100.0F, k).texture(1.0F, 1.0F);
                    bufferBuilder.vertex(matrix4f2, -k, 100.0F, k).texture(0.0F, 1.0F);
                    BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
                    k = 20.0F;
                    RenderSystem.setShaderTexture(0, WorldRendererAccess.getMoonPhases());
                    int r = this.world.getMoonPhase();
                    int s = r % 4;
                    int m = r / 4 % 2;
                    float t = (float)(s + 0) / 4.0F;
                    float o = (float)(m + 0) / 2.0F;
                    float p = (float)(s + 1) / 4.0F;
                    float q = (float)(m + 1) / 2.0F;

                    BufferBuilder bufferBuilder2 = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    bufferBuilder2.vertex(matrix4f2, -k, -100.0F, k).texture(p, q);
                    bufferBuilder2.vertex(matrix4f2, k, -100.0F, k).texture(t, q);
                    bufferBuilder2.vertex(matrix4f2, k, -100.0F, -k).texture(t, o);
                    bufferBuilder2.vertex(matrix4f2, -k, -100.0F, -k).texture(p, o);
                    BufferRenderer.drawWithGlobalProgram(bufferBuilder2.end());
                    float u = this.world.getStarBrightness(tickDelta) * i;
                    if (u > 0.0F) {
                        RenderSystem.setShaderColor(u, u, u, u);
                        BackgroundRenderer.clearFog();
                        worldRendererAccess.getStarsBuffer().bind();
                        worldRendererAccess.getStarsBuffer().draw(matrices.peek().getPositionMatrix(), projectionMatrix, GameRenderer.getPositionProgram());
                        VertexBuffer.unbind();
                        fogCallback.run();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                    matrices.pop();
                    RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
                    double d = this.client.player.getCameraPosVec(tickDelta).y - this.world.getLevelProperties().getSkyDarknessHeight(this.world);
                    if (d < 0.0) {
                        matrices.push();
                        matrices.translate(0.0F, 12.0F, 0.0F);
                        worldRendererAccess.getDarkSkyBuffer().bind();
                        worldRendererAccess.getDarkSkyBuffer().draw(matrices.peek().getPositionMatrix(), projectionMatrix, shaderProgram);
                        VertexBuffer.unbind();
                        matrices.pop();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.depthMask(true);
                }
            }
        }
    }

    private boolean hasBlindnessOrDarkness(Camera camera) {
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) || livingEntity.hasStatusEffect(StatusEffects.DARKNESS);
        }
        return false;
    }

    private void render(MatrixStack matrixStack, World world, float tickDelta) {
        int timeOfDay = (int) (world.getTimeOfDay() % 24000L);
        float skyAngle = world.getSkyAngle(tickDelta);
        float rainGradient = world.getRainGradient(tickDelta);
        float thunderGradient = world.getThunderGradient(tickDelta);

        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer optiFineSkyLayer : this.layers) {
            if (optiFineSkyLayer.isActive(timeOfDay)) {
                optiFineSkyLayer.render(world, matrixStack, timeOfDay, skyAngle, rainGradient, thunderGradient);
            }
        }

        float f3 = 1.0F - rainGradient;
        OptiFineBlend.ADD.getBlendFunc().accept(f3);
    }

    @Override
    public void tick(ClientWorld clientWorld) {
        this.active = true;
        if (clientWorld.getRegistryKey() != this.worldIdentifier) {
            this.layers.forEach(layer -> layer.setConditionAlpha(-1.0F));
            this.active = false;
        } else {
            this.layers.forEach(layer -> layer.tick(clientWorld));
        }
    }

    @Override
    public boolean isActive() {
        return FSBInteropConfig.INSTANCE.interoperability && this.active;
    }

    public List<OptiFineSkyLayer> getLayers() {
        return layers;
    }

    public RegistryKey<World> getWorldIdentifier() {
        return worldIdentifier;
    }
}
