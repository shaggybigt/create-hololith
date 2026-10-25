package dev.hololith.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** Owns one projection mesh, uploaded once and reused until closed. */
final class ProjectionBuffer implements AutoCloseable {
    private final VertexBuffer buffer;
    private volatile boolean closed;

    private ProjectionBuffer(VertexBuffer buffer) {
        this.buffer = buffer;
    }

    /** Consumes the supplied mesh, including when creation fails. */
    static ProjectionBuffer create(MeshData mesh) {
        if (mesh == null) {
            return null;
        }

        VertexBuffer allocated = null;
        boolean handedToUpload = false;
        boolean retained = false;
        try {
            allocated = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try {
                allocated.bind();
                // VertexBuffer.upload closes the mesh on success and failure.
                handedToUpload = true;
                allocated.upload(mesh);
            } finally {
                VertexBuffer.unbind();
            }
            ProjectionBuffer result = new ProjectionBuffer(allocated);
            retained = true;
            return result;
        } finally {
            try {
                if (!handedToUpload) {
                    mesh.close();
                }
            } finally {
                if (!retained && allocated != null) {
                    allocated.close();
                }
            }
        }
    }

    void render(RenderType renderType, Matrix4f modelView) {
        if (closed) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        try {
            renderType.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                buffer.bind();
                try {
                    buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
                } catch (RuntimeException | Error failure) {
                    // Minecraft clears the shader only after a successful draw.
                    try {
                        shader.clear();
                    } catch (RuntimeException | Error cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    throw failure;
                }
            }
        } finally {
            try {
                VertexBuffer.unbind();
            } finally {
                renderType.clearRenderState();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            buffer.close();
        } else {
            closed = true;
            RenderSystem.recordRenderCall(buffer::close);
        }
        closed = true;
    }
}
